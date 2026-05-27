# Sprint 4 도메인 B — 멤버 관리 클래스 설계

> **Scope.** Sprint 4 D-020 §20.a 의 도메인 B 3개 엔드포인트 (팀원 목록 / 강퇴 / 탈퇴) 구현을 위한 클래스 토폴로지 / 메서드 시그니처 / DTO·Entity 필드 / 에러 코드 / 동시성 정책.
>
> **목적.** 본 문서 결과물이 다음 단계(테스트 작성)의 입력. 외부 시그니처와 계약만 픽싱.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 작성). 2차: 최평화(클라이언트 흐름 확인).
>
> **전제 결정.** `docs/decisions-log.md` D-003 / D-010 ~ D-020, `docs/claude-context.md` §1~§3, `docs/application-class-design.md` (도메인 A). 도메인 C(게시판)는 본 문서 범위 밖 (별도 설계 문서).
>
> **본 문서 대상 엔드포인트 (3건).**
> - `GET    /api/teams/{teamId}/members`                    — 팀원 목록 (활성 멤버 + LEADER 권한 무관 모두 조회)
> - `DELETE /api/teams/{teamId}/members/{memberId}`         — 강퇴 (LEADER)
> - `DELETE /api/teams/{teamId}/members/me`                 — 본인 탈퇴
>
> **`teamId` 의미.** D-010 에 따라 `teamId == studyId`. 컨트롤러 진입 직후 동일 변수로 취급.

---

## 1. 패키지 토폴로지

도메인 A 의 `application` 패키지와 독립. 본 도메인은 기존 `com.studymate.study` 패키지에 흡수 — 엔티티 (`StudyMember`) 가 이미 `study` 도메인 소속이고, 멤버 관리는 스터디 라이프사이클의 일부.

```
com.studymate.study
├── controller
│   └── StudyMemberController       // /api/teams/{teamId}/members/*  (신규)
├── service
│   └── StudyMemberService          // 3 메서드 단일 진입점          (신규)
├── domain
│   ├── StudyMember                 // 기존 — 도메인 메서드 보강
│   └── MemberLeftReason            // enum {VOLUNTARY, KICKED}      (신규)
├── repository
│   └── StudyMemberRepository       // 기존 — 쿼리 4건 추가
└── dto
    ├── response
    │   ├── MemberSummaryResponse                                    (신규)
    │   └── MemberListResponse                                       (신규)
    └── (request DTO 없음 — DELETE 본문 없음)
```

공통 패키지: `com.studymate.common` (ErrorCode / GlobalExceptionHandler 재사용).

**도메인 A 와의 패키지 분리 사유.** 도메인 A 는 `application` 엔티티가 메인이라 별도 패키지. 도메인 B 는 `study_member` 엔티티 중심이고, Sprint 3 에서 이미 `study` 패키지에 `StudyMember` 가 자리잡았으므로 신규 패키지 없이 흡수. 라이프사이클 일관성 ↑.

---

## 2. 클래스별 책임 + 의존성

### 2.1 StudyMemberController
- 책임: 팀원 조회/강퇴/탈퇴의 HTTP 어댑터. `@RequestMapping("/api/teams/{teamId}/members")`.
- 메서드: `list`, `kick`, `leave`.
- 의존: `StudyMemberService`.
- 인증: 모든 메서드 필수 (D-015 / D-020 정합).

### 2.2 StudyMemberService
- 책임: 3개 엔드포인트의 도메인 트랜잭션 조립. D-020 §20.c (락) / §20.i (강퇴/탈퇴 갱신) / §20.j (LEADER 탈퇴 금지) 의 단일 진입점.
- 의존:
  - `StudyMemberRepository`
  - `StudyRepository` (study row 락 + `current_member_count` 감소)
  - `com.studymate.auth.repository.UserRepository` (팀원 이름 응답용)
- 트랜잭션 경계: 메서드 1건 = 트랜잭션 1건. 조회는 `readOnly=true`.

### 2.3 Repository 계층
- Spring Data JPA. 본 도메인 커스텀 쿼리는 §6.
- `study` row 락은 `StudyRepository.findByIdAndNotDeletedForUpdate` 재사용 (Sprint 3).

---

## 3. 데이터 플로우 (시나리오별 컴포넌트 협력)

### 3.1 팀원 목록 (`GET /api/teams/{teamId}/members`)

```
StudyMemberController.list(userId, teamId)
  → StudyMemberService.list(userId, studyId=teamId)
    → (TX 시작, readOnly)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → assertActiveMember(studyId, userId)            // 활성 멤버만 조회 가능
        ⇡ 아니면 FORBIDDEN
    → StudyMemberRepository.findAllActiveByStudyIdOrderByJoinedAt(studyId)
    → UserRepository.findAllByIdIn(memberUserIds)    // 이름 batch fetch
  ← MemberListResponse(members[], totalCount)
```

본 스프린트 노출 대상: **활성 멤버만 (`is_active=1`)**. 탈퇴/강퇴된 과거 row 는 응답에 포함하지 않음. 페이징 없음 — 한 스터디의 활성 멤버 상한 50 (D-015) 이므로 전수 반환.

정렬: `joined_at ASC` (가입 순). LEADER 가 가장 먼저 INSERT 되므로 자연스럽게 첫 row.

### 3.2 강퇴 (`DELETE /api/teams/{teamId}/members/{memberId}`)

```
StudyMemberController.kick(userId, teamId, memberId) → 204
  → StudyMemberService.kick(callerUserId=userId, studyId=teamId, memberId)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)
        ⇡ 없으면 NOT_FOUND
    → leaderMember = StudyMemberRepository.findActiveManager(studyId, callerUserId)
        ⇡ 없으면 FORBIDDEN                            // can_manage_member 보유자만
    → target = StudyMemberRepository.findByIdAndStudyIdForUpdate(memberId, studyId)
        ⇡ 없으면 NOT_FOUND
    → (target.isActive == false) → NOT_FOUND          // 이미 탈퇴/강퇴된 멤버
    → ("LEADER".equals(target.roleCode)) → CANNOT_REMOVE_LEADER   // D-020 §20.i
    → target.kick()                                   // is_active=0, left_reason='KICKED', left_at=now()
    → study.decrementMemberCount()                    // current_member_count -= 1
    → (TX 커밋)
```

권한: `study_member.is_active=1 AND study_role.can_manage_member=1` (LEADER). `findActiveManager` JPQL join 으로 검증 (도메인 A 의 `findActiveApprover` 와 동일 패턴).

LEADER 본인이 본인을 강퇴하는 시나리오는 위 분기에서 `CANNOT_REMOVE_LEADER` 로 차단. 강퇴자 ≠ 대상자 분기는 별도로 두지 않음 (target 이 LEADER 면 자기 자신 포함 어떤 LEADER 도 강퇴 불가).

자동 마감 역복구 (CLOSED → OPEN) 는 **하지 않음** (D-020 §20.b).

### 3.3 탈퇴 (`DELETE /api/teams/{teamId}/members/me`)

```
StudyMemberController.leave(userId, teamId) → 204
  → StudyMemberService.leave(userId, studyId=teamId)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)
        ⇡ 없으면 NOT_FOUND
    → self = StudyMemberRepository.findActiveByStudyIdAndUserIdForUpdate(studyId, userId)
        ⇡ 없으면 NOT_FOUND
    → ("LEADER".equals(self.roleCode)) → CONFLICT "팀장은 탈퇴할 수 없습니다."   // D-020 §20.j
    → self.leave()                                   // is_active=0, left_reason='VOLUNTARY', left_at=now()
    → study.decrementMemberCount()
    → (TX 커밋)
```

`CONFLICT` 발신 시 ErrorCode 별도 신설 없이 기존 `CONFLICT` 재사용 + 메시지 override (D-020 §20.j 어휘). `ErrorCode.CONFLICT.defaultMessage` 가 "이미 가입된 이메일입니다." 라 본 케이스만 메시지 오버라이드 필요 — `BusinessException(ErrorCode.CONFLICT, "팀장은 탈퇴할 수 없습니다.")` 패턴.

자동 마감 역복구 (CLOSED → OPEN) 는 **하지 않음** (D-020 §20.b).

---

## 4. Controller — 메서드 시그니처

### 4.1 StudyMemberController

`@RestController`, `@RequestMapping("/api/teams/{teamId}/members")`, `@RequiredArgsConstructor`, `@Validated`.

```java
@GetMapping
public MemberListResponse list(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId
);

@DeleteMapping("/{memberId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void kick(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long memberId
);

@DeleteMapping("/me")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void leave(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId
);
```

`memberId` = `study_member.id` (PK), `userId` 아님. 강퇴 대상을 `study_member` 행 단위로 식별.

---

## 5. StudyMemberService — public 메서드 시그니처

```java
@Transactional(readOnly = true)
public MemberListResponse list(long callerUserId, long studyId);

@Transactional
public void kick(long callerUserId, long studyId, long memberId);

@Transactional
public void leave(long userId, long studyId);
```

본 도메인은 `*Command` 입력 객체 없음 — request body 가 없는 엔드포인트 3건.

---

## 6. Repository — 쿼리 메서드 시그니처

### 6.1 StudyMemberRepository — 기존 + 추가

Sprint 3 / 도메인 A 의 기존 메서드 외 본 도메인 신규:

```java
// §3.1 목록 — 활성 멤버 가입순
@Query("""
    select sm from StudyMember sm
    where sm.studyId = :studyId
      and sm.isActive = true
    order by sm.joinedAt asc
""")
List<StudyMember> findAllActiveByStudyIdOrderByJoinedAt(@Param("studyId") long studyId);

// §3.2 강퇴 대상 락 조회
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select sm from StudyMember sm
    where sm.id = :id and sm.studyId = :studyId
""")
Optional<StudyMember> findByIdAndStudyIdForUpdate(@Param("id") long id,
                                                  @Param("studyId") long studyId);

// §3.3 본인 활성 멤버 락 조회
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select sm from StudyMember sm
    where sm.studyId = :studyId
      and sm.userId  = :userId
      and sm.isActive = true
""")
Optional<StudyMember> findActiveByStudyIdAndUserIdForUpdate(@Param("studyId") long studyId,
                                                            @Param("userId") long userId);

// §3.2 권한 검증 — can_manage_member
@Query("""
    select sm from StudyMember sm, com.studymate.application.domain.StudyRole r
    where r.code = sm.roleCode
      and sm.studyId = :studyId
      and sm.userId  = :userId
      and sm.isActive = true
      and r.canManageMember = true
""")
Optional<StudyMember> findActiveManager(@Param("studyId") long studyId,
                                        @Param("userId") long userId);
```

기존 `existsByStudyIdAndUserIdAndIsActiveTrue` 재사용 (§3.1 의 `assertActiveMember`).

### 6.2 StudyRole — 추가 매핑 필드

도메인 A 에서 `canApproveApplication` 만 매핑했으나, 본 도메인이 `canManageMember` 사용 → 이미 §7.3 (`application-class-design.md`) 에 매핑된 필드라 추가 작업 없음. 확인만.

---

## 7. Entity 필드

### 7.1 StudyMember (기존) — 도메인 메서드 보강

기존 필드 (Sprint 3) 유지. **신규 도메인 메서드 추가**:

```java
public void kick() {
    if (!this.isActive) throw new IllegalStateException("already inactive");
    this.isActive    = false;
    this.leftReason  = MemberLeftReason.KICKED.name();
    this.leftAt      = LocalDateTime.now();
    this.updatedAt   = this.leftAt;
}

public void leave() {
    if (!this.isActive) throw new IllegalStateException("already inactive");
    this.isActive    = false;
    this.leftReason  = MemberLeftReason.VOLUNTARY.name();
    this.leftAt      = LocalDateTime.now();
    this.updatedAt   = this.leftAt;
}

public boolean isLeader() { return "LEADER".equals(this.roleCode); }
```

`StudyMember` 의 leftAt / leftReason / updatedAt getter 도 노출 (지금까지 미노출). leftReason 은 String 이라 도메인 어휘 변환은 caller (필요 시) 가 담당.

### 7.2 MemberLeftReason (enum, 신규)

```java
public enum MemberLeftReason {
    VOLUNTARY,   // 본인 탈퇴
    KICKED,      // LEADER 강퇴
    STUDY_CLOSED // 스터디 종료 — 본 스프린트 범위 외 (D-020 §20.i 후속)
}
```

DB 컬럼은 `VARCHAR(20)` 그대로 (D-017). enum 은 코드 가독성용. `STUDY_CLOSED` 는 본 스프린트에서 사용하지 않으나 향후 일관성 위해 미리 등재.

### 7.3 Study (기존) — 메서드 사용

`Study.decrementMemberCount()` 메서드가 Sprint 3 에 이미 있다고 가정. 없으면 도메인 B 에서 추가:

```java
public void decrementMemberCount() {
    if (this.currentMemberCount <= 0) throw new IllegalStateException("member count underflow");
    this.currentMemberCount -= 1;
}
```

(현재 LEADER 가 항상 1명 활성으로 남으므로 underflow 는 정상 흐름에서 발생하지 않음.)

---

## 8. Request / Response DTO

### 8.1 응답 DTO

```java
public record MemberSummaryResponse(
    long          memberId,           // study_member.id
    long          userId,
    String        userName,
    String        roleCode,           // "LEADER" / "MEMBER"
    LocalDateTime joinedAt
) {}

public record MemberListResponse(
    List<MemberSummaryResponse> members,
    int                          totalCount
) {}
```

`totalCount` = `members.size()` 그대로. 페이징 미적용 (§3.1).

### 8.2 Request DTO — 없음

DELETE 본문 없음. `kick` / `leave` 는 path 만으로 식별.

---

## 9. ErrorCode enum

기존 `com.studymate.common.exception.ErrorCode` 에 **1건 추가** (D-020 §20.i 정합).

| 코드 | HTTP | 메시지 (default) | 발생 위치 |
|---|---|---|---|
| `CANNOT_REMOVE_LEADER`  | 409 | "팀장은 강퇴할 수 없습니다." | §3.2 강퇴 대상이 LEADER |

LEADER 본인 탈퇴 (§3.3) 는 기존 `CONFLICT` 재사용 + 메시지 오버라이드 ("팀장은 탈퇴할 수 없습니다."). D-020 §20.j 의 "별도 ErrorCode 신설 없음" 정합.

기존 도메인의 `NOT_FOUND` / `FORBIDDEN` 재사용.

---

## 10. 동시성 / 트랜잭션 정책

### 10.1 비관적 락 (PESSIMISTIC_WRITE) 적용 지점

| 메서드 | study row | study_member row |
|---|---|---|
| `list`   | — (readOnly) | — |
| `kick`   | ✅ `findByIdAndNotDeletedForUpdate` | ✅ `findByIdAndStudyIdForUpdate` (target) |
| `leave`  | ✅ `findByIdAndNotDeletedForUpdate` | ✅ `findActiveByStudyIdAndUserIdForUpdate` (self) |

`study` row 락 사유 (D-020 §20.c):
- `current_member_count` 동시 감소 직렬화.
- 동시 수락(도메인 A) / 강퇴(도메인 B) / 탈퇴(도메인 B) 가 같은 study row 락을 두고 직렬화 → 카운트/자동 마감 일관성 보장.

`study_member` row 락:
- 두 LEADER 가 동시에 같은 target 을 강퇴 시도 → 한 쪽이 락 대기 후 `is_active` 재확인 → 이미 inactive → `NOT_FOUND`.
- 본인 탈퇴를 두 클라이언트 (같은 user 가 다른 디바이스) 에서 동시 시도 → 한 쪽 락 대기 → 두 번째 호출 `NOT_FOUND`.

### 10.2 트랜잭션 경계

- 메서드 1건 = 트랜잭션 1건.
- `kick` / `leave` 모두 `study_member UPDATE` + `study UPDATE` (count 감소) 한 트랜잭션.
- 조회 (`list`) 는 `readOnly = true`.

### 10.3 DB 마이그레이션

**변경 없음** (D-020 §20.l). 기존 `study_member` 테이블 그대로 사용. `left_reason` VARCHAR(20) 도 그대로.

JPA 신규 매핑 없음. `StudyMember` 엔티티에 도메인 메서드만 추가 (§7.1).

### 10.4 동시성 회귀 테스트

D-009 / D-020 §20.c 기조 유지: 코드 락만으로 닫는다. 통합 멀티스레드 회귀 테스트 작성하지 않음.

---

## 11. Trade-off Analysis

### 11.1 패키지 = `study` 흡수 vs 신규 `member` 패키지
- **채택**: `com.studymate.study` 흡수.
- 비용: `study` 패키지 클래스 수 증가.
- 대안: `com.studymate.member` 신규 패키지.
- 근거: `StudyMember` 엔티티가 이미 `study` 도메인 소속. 멤버 라이프사이클 = 스터디 라이프사이클의 부분집합. 분리는 인지부담 ↑.

### 11.2 강퇴/탈퇴 = soft delete (is_active=0) vs 물리 삭제
- **채택**: soft delete (스키마 정합).
- 비용: `study_member` row 누적.
- 대안: 물리 삭제.
- 근거: D-017 / 스키마 코멘트 ("history 물리 삭제 금지") 정합. 향후 출석/통계 도메인이 과거 멤버십을 참조할 수 있음.

### 11.3 LEADER 탈퇴 = 금지 vs LEADER 위임 후 탈퇴
- **채택**: 금지 (D-020 §20.j).
- 비용: LEADER 가 빠지려면 스터디 삭제만 가능.
- 대안: 본 스프린트에서 LEADER 위임 + 탈퇴 흐름 구현.
- 근거: D-020 §20.j 명시. 위임은 별도 권한 모델 변경이라 본 스프린트 외.

### 11.4 멤버 카운트 자동 복구 (CLOSED → OPEN) 안 함
- **채택**: 강퇴/탈퇴로 카운트가 줄어도 status 변경 안 함 (D-020 §20.b).
- 비용: 정원 채워 마감된 스터디에서 멤버가 빠져도 신규 지원 불가 (LEADER 가 수동 PATCH 로 OPEN 전환 필요).
- 대안: 자동 OPEN 복귀.
- 근거: D-014 의 수동 토글 유지. 자동 변경은 의도하지 않은 재모집 위험.

### 11.5 강퇴 권한 = `can_manage_member` 검사 vs `roleCode == 'LEADER'` 하드코딩
- **채택**: `can_manage_member` JPQL join 검사.
- 비용: `study_role` 테이블 join 1회.
- 대안: `roleCode.equals("LEADER")`.
- 근거: 도메인 A `findActiveApprover` 와 동일 패턴 유지. 향후 권한 분리 (CO_LEADER 등) 시 본 매핑 그대로 동작.

---

## 12. D-020 처리 결과 (2026-05-27)

| 결정 | 본 문서 반영 위치 |
|---|---|
| D-020 §20.a 도메인 B 3 엔드포인트 | §1 (패키지) / §4 (Controller) / §5 (Service) |
| D-020 §20.b 자동 마감 역복구 안 함 | §3.2 / §3.3 / §11.4 |
| D-020 §20.c 동시성 — study row + member row PESSIMISTIC_WRITE | §10.1 / §10.2 |
| D-020 §20.i 강퇴/탈퇴 갱신 + CANNOT_REMOVE_LEADER | §3.2 / §3.3 / §7.1 / §9 |
| D-020 §20.j LEADER 탈퇴 금지 (CONFLICT 어휘) | §3.3 / §9 / §11.3 |
| D-020 §20.l DB 스키마 변경 없음 | §10.3 |

---

## 13. 다음 단계로 넘기는 입력 체크리스트

테스트 작성 진입 시 본 문서 + 아래 항목 입력:

- [x] 3개 엔드포인트 시그니처 (§4)
- [x] Service public 메서드 시그니처 (§5)
- [x] Repository 쿼리 메서드 시그니처 (§6)
- [x] Entity 도메인 메서드 (§7)
- [x] Response DTO 모양 (§8)
- [x] ErrorCode 신규 1건 + 기존 CONFLICT 메시지 오버라이드 (§9)
- [x] 동시성/트랜잭션 정책 (§10.1, §10.2)
- [x] DB 마이그레이션 — 없음 (§10.3)
- [ ] 테스트 플랜 (`docs/member-test-plan.md`) — 다음 단계 산출물
- [ ] 도메인 C (게시판) 클래스 설계 — B 구현 완료 후 진입

테스트 매핑 후보 ID (SRS 검사기준 ↔ 본 설계):
- RE-SF4-04 (강퇴) ↔ §3.2 / `DELETE /api/teams/{teamId}/members/{memberId}`
- RE-SF4-05 (탈퇴) ↔ §3.3 / `DELETE /api/teams/{teamId}/members/me`
- 시트 팀원 목록 ↔ §3.1 / `GET /api/teams/{teamId}/members`
