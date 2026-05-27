# Sprint 4 도메인 B — 멤버 관리 테스트 플랜

> **목적.** 다음 단계(테스트 코드 작성)의 입력. `docs/member-class-design.md` 의 외부 시그니처 + SRS RE-SF4-04/05 검사기준 + D-020 결정을 테스트 케이스 ID 단위로 깎아둔다.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 코드 작성).
>
> **Scope 외 명시.** 부하 · E2E · UI · 보안 침투 · **동시성 회귀 테스트(D-009 / D-020 §20.c)** · 알림(P-009).

---

## 목차

1. 입력 체크리스트
2. 테스트 피라미드 매핑 + 컴포넌트별 전략
3. 도구·픽스처·테스트 더블 결정
4. 시나리오별 테스트 케이스 (member-class-design §3 1:1)
   - 4.1 팀원 목록 (`GET /api/teams/{teamId}/members`)
   - 4.2 강퇴 (`DELETE /api/teams/{teamId}/members/{memberId}`)
   - 4.3 탈퇴 (`DELETE /api/teams/{teamId}/members/me`)
5. 입력 유효성 테스트 매트릭스 (RE-NF-03)
6. SRS 검사기준 ↔ 테스트 매핑 표
7. 커버 / 스킵 정책
8. 커버리지 목표
9. 다음 단계 진입 체크리스트

---

## 1. 입력 체크리스트

- [x] `docs/member-class-design.md` §2 ~ §10
- [x] `docs/decisions-log.md` D-020 (§20.a / §20.b / §20.c / §20.i / §20.j / §20.l)
- [x] SRS RE-SF4-04 / RE-SF4-05 검사기준
- [x] `studymate_schema.sql` 의 `study_member` 컬럼 (마이그레이션 없음)
- [x] D-020 §20.k 외 신규 ErrorCode 1건 (`CANNOT_REMOVE_LEADER`)
- [x] Sprint 3 / 도메인 A 인프라 (StudyFixture, StudyMemberFixture, UserFixture, JWT) 재사용
- [x] `study_role` 시드 (`LEADER` `can_manage_member=1`, `MEMBER` `can_manage_member=0`)

---

## 2. 테스트 피라미드 매핑 + 컴포넌트별 전략

| 컴포넌트 | 테스트 타입 | 이유 |
|---|---|---|
| `StudyMemberController` | `@WebMvcTest` 슬라이스 + `MockMvc`, Service mock | 라우팅 / HTTP status / JSON 직렬화 / `@AuthenticationPrincipal` 주입. |
| `StudyMemberService` | 순수 Mockito 단위 | 권한 분기 (`findActiveManager`) / LEADER 강퇴 차단 / LEADER 탈퇴 차단 / count 감소 호출 순서. |
| Entity 도메인 메서드 (`StudyMember.kick` / `leave` / `isLeader`) | 순수 단위 | is_active=0, left_reason, left_at, updatedAt 갱신 정합 + 이미 inactive 한 row 에 호출 시 예외. |
| Repository | `@DataJpaTest` (H2) | `findAllActiveByStudyIdOrderByJoinedAt` 정렬 · `findByIdAndStudyIdForUpdate` · `findActiveByStudyIdAndUserIdForUpdate` · `findActiveManager` JPQL join. |
| 전체 시나리오 3종 | `@SpringBootTest` + MockMvc + H2 | 컨트롤러 → DB. SRS 검사기준 닫는 위치. |

**검증 종류.**
- *동작*: HTTP status / body / ErrorCode 문자열 / 메시지 (CONFLICT 오버라이드 검증).
- *상태*: `study_member.is_active` / `left_reason` / `left_at` / `updated_at`, `study.current_member_count`, `study.status` (변경 없음 확인).
- *상호작용*: Service 내부 호출 순서 — `studyRepository.findByIdAndNotDeletedForUpdate` → `findActiveManager` (kick) or `findActiveByStudyIdAndUserIdForUpdate` (leave) → target 검증 분기 → `target.kick()`/`target.leave()` → `study.decrementMemberCount()`.

---

## 3. 도구·픽스처·테스트 더블 결정

| 항목 | 결정 | 비고 |
|---|---|---|
| 통합/슬라이스 DB | **H2** (`MODE=MYSQL`) | Sprint 2·3·도메인 A 동일. |
| 시간 | `Clock` 빈 주입. `left_at` 시점 어서션은 `Clock.fixed`. | |
| 인증 컨텍스트 | LEADER user A / 일반 멤버 user B / 또다른 멤버 user D / 비-멤버 user C 4종 fixture. | 강퇴 자/대상 분리 + 본인 탈퇴 + 비-멤버 차단 시나리오. |
| 픽스처 | `StudyMemberFixture.leader(studyId, userId)` / `member(studyId, userId)` / `inactiveMember(studyId, userId, reason)` (신규). `StudyFixture.openStudy(...)` / `closedStudy(...)` 재사용. | |
| 통합 테스트 트랜잭션 | `@Transactional` 사용. 커밋 후 상태 어서션 시 `entityManager.clear()` 후 `findById`. | Sprint 3 동일 패턴. |
| `study_role` 시드 | `LEADER` (`can_manage_member=1`), `MEMBER` (`can_manage_member=0`) 최소 2건 INSERT 보장. | `findActiveManager` JPQL join 동작 필수. |

---

## 4. 시나리오별 테스트 케이스

ID 형식: `T-MEM-<엔드포인트약자>-<번호>`.
레이어 약자: `U` = 단위, `WMVC` = `@WebMvcTest`, `DJ` = `@DataJpaTest`, `SBT` = `@SpringBootTest` 통합.

### 4.1 팀원 목록 (`GET /api/teams/{teamId}/members`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-MEM-LIST-01 | SBT | 사전: study (LEADER=A joinedAt T0, MEMBER=B joinedAt T1, MEMBER=D joinedAt T2). user A 호출. | 200. `members.length=3`, `joinedAt ASC` (A, B, D), 각 row 에 `memberId`, `userId`, `userName`, `roleCode`, `joinedAt`. `totalCount=3`. | 동작/상태 |
| T-MEM-LIST-02 | SBT | 사전: 활성 3 + 강퇴된 1 (`is_active=0`). user A 호출. | length=3 (강퇴 멤버 제외). | 동작/상태 |
| T-MEM-LIST-03 | SBT | 사전: LEADER 1명만. user A 호출. | length=1, roleCode="LEADER". | 동작 |
| T-MEM-LIST-04 | SBT | 비-멤버 user C 호출. | 403 `FORBIDDEN`. | 동작 |
| T-MEM-LIST-05 | SBT | 탈퇴한 user B 호출 (`is_active=0`). | 403 `FORBIDDEN`. | 동작 |
| T-MEM-LIST-06 | SBT | MEMBER user B 호출 (활성). | 200. (활성 멤버면 LEADER 권한 무관 조회 가능.) | 동작 |
| T-MEM-LIST-07 | SBT | 미존재 teamId. | 404 `NOT_FOUND`. | 동작 |
| T-MEM-LIST-08 | SBT | soft-deleted study. | 404 `NOT_FOUND`. | 동작 |
| T-MEM-LIST-09 | SBT | 인증 헤더 없음. | 401 `UNAUTHORIZED`. | 동작 |
| T-MEM-LIST-10 | DJ  | `findAllActiveByStudyIdOrderByJoinedAt` | 활성만 / `joinedAt ASC` 검증. | 동작 |

### 4.2 강퇴 (`DELETE /api/teams/{teamId}/members/{memberId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-MEM-KICK-01 | SBT | OPEN study (LEADER=A, MEMBER=B, cur=2, max=5). A 가 B 의 memberId 로 강퇴. | 204. `study_member(B).is_active=0`, `left_reason="KICKED"`, `left_at` 채워짐. `study.current_member_count=1`. `study.status=OPEN` (역복구 없음). | 동작/상태 |
| T-MEM-KICK-02 | SBT | CLOSED study (cur=max=2), A 가 B 강퇴. | 204. count=1. `study.status=CLOSED` (자동 OPEN 복귀 없음 — D-020 §20.b). | 동작/상태 (D-020 §20.b) |
| T-MEM-KICK-03 | SBT | A 가 자기 자신(LEADER memberId) 강퇴 시도. | 409 `CANNOT_REMOVE_LEADER`. 상태 무변경. | 동작/상태 (D-020 §20.i) |
| T-MEM-KICK-04 | SBT | 멤버 2 LEADER 시나리오 가정 — 본 스프린트엔 발생하지 않으나, target.roleCode="LEADER" 인 다른 row 강퇴 시도. | 409 `CANNOT_REMOVE_LEADER`. | 동작 |
| T-MEM-KICK-05 | SBT | target 이 이미 inactive (`is_active=0`). | 404 `NOT_FOUND`. | 동작/상태 |
| T-MEM-KICK-06 | SBT | target 의 studyId 가 path teamId 와 다름. | 404 `NOT_FOUND` (다른 스터디의 멤버 강퇴 차단). | 동작 |
| T-MEM-KICK-07 | SBT | 비-LEADER user B 가 다른 멤버 D 강퇴 시도. | 403 `FORBIDDEN`. 상태 무변경. | 동작/상태 |
| T-MEM-KICK-08 | SBT | 비-멤버 user C 호출. | 403 `FORBIDDEN`. | 동작 |
| T-MEM-KICK-09 | SBT | 미존재 memberId. | 404 `NOT_FOUND`. | 동작 |
| T-MEM-KICK-10 | SBT | 미존재 teamId. | 404 `NOT_FOUND`. | 동작 |
| T-MEM-KICK-11 | SBT | soft-deleted study. | 404 `NOT_FOUND`. | 동작 |
| T-MEM-KICK-12 | SBT | 인증 헤더 없음. | 401 `UNAUTHORIZED`. | 동작 |
| T-MEM-KICK-13 | U (Service) | mock 리포지토리. kick 호출 순서: `studyRepository.findByIdAndNotDeletedForUpdate` → `findActiveManager` → `findByIdAndStudyIdForUpdate` (target) → target.isActive 분기 → LEADER 분기 → `target.kick()` → `study.decrementMemberCount()` | Mockito `InOrder`. | 상호작용 |
| T-MEM-KICK-14 | U (Service) | mock. target.roleCode="LEADER" 분기. `target.kick()` / `decrementMemberCount` 호출되지 않음. | `verify(target, never()).kick()` + `verify(study, never()).decrementMemberCount()` | 상호작용 |
| T-MEM-KICK-15 | U (Entity) | `StudyMember.kick()` 단위 — is_active=true 초기 상태. | is_active=false, left_reason="KICKED", left_at != null, updated_at == left_at. | 상태 |
| T-MEM-KICK-16 | U (Entity) | `StudyMember.kick()` — 이미 inactive 인 row. | `IllegalStateException`. | 동작 |

### 4.3 탈퇴 (`DELETE /api/teams/{teamId}/members/me`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-MEM-LEAVE-01 | SBT | OPEN study (LEADER=A, MEMBER=B, cur=2, max=5). user B 호출. | 204. `study_member(B).is_active=0`, `left_reason="VOLUNTARY"`, `left_at` 채워짐. `study.current_member_count=1`. `study.status=OPEN`. | 동작/상태 |
| T-MEM-LEAVE-02 | SBT | CLOSED study (cur=max=2). user B 탈퇴. | 204. count=1. `study.status=CLOSED` (역복구 없음). | 동작/상태 |
| T-MEM-LEAVE-03 | SBT | LEADER user A 가 본인 탈퇴 시도. | 409. body `{"code":"CONFLICT","message":"팀장은 탈퇴할 수 없습니다."}`. 상태 무변경. | 동작/상태 (D-020 §20.j) |
| T-MEM-LEAVE-04 | SBT | 활성 멤버 아님 (이미 탈퇴 / 비-멤버). | 404 `NOT_FOUND`. | 동작/상태 |
| T-MEM-LEAVE-05 | SBT | 미존재 teamId. | 404 `NOT_FOUND`. | 동작 |
| T-MEM-LEAVE-06 | SBT | soft-deleted study. | 404 `NOT_FOUND`. | 동작 |
| T-MEM-LEAVE-07 | SBT | 인증 헤더 없음. | 401 `UNAUTHORIZED`. | 동작 |
| T-MEM-LEAVE-08 | U (Service) | mock. leave 호출 순서: `studyRepository.findByIdAndNotDeletedForUpdate` → `findActiveByStudyIdAndUserIdForUpdate` (self) → LEADER 분기 → `self.leave()` → `study.decrementMemberCount()` | Mockito `InOrder`. | 상호작용 |
| T-MEM-LEAVE-09 | U (Service) | mock. self.roleCode="LEADER" 분기. `self.leave()` / `decrementMemberCount` 호출되지 않음. | Mockito verify never. 던지는 예외 = `BusinessException(ErrorCode.CONFLICT, "팀장은 탈퇴할 수 없습니다.")`. | 상호작용/동작 |
| T-MEM-LEAVE-10 | U (Entity) | `StudyMember.leave()` 단위. | is_active=false, left_reason="VOLUNTARY", left_at != null. | 상태 |
| T-MEM-LEAVE-11 | DJ  | `findActiveByStudyIdAndUserIdForUpdate` | 활성만 리턴 / is_active=0 row 는 empty. | 동작 |
| T-MEM-LEAVE-12 | DJ  | `findActiveManager` | `LEADER` (can_manage=1) 활성만 / `MEMBER` 또는 inactive 는 empty. | 동작 |

---

## 5. 입력 유효성 테스트 매트릭스 (RE-NF-03)

`@WebMvcTest` + `MockMvc` 슬라이스 `MemberRequestValidationTest` 한 클래스.

| 엔드포인트 | 케이스 | 기대 |
|---|---|---|
| GET /members | (입력 없음 — body/쿼리 없음) | — |
| DELETE /members/{memberId} | `memberId` 비숫자 (`abc`) | 400 (Spring MVC 자동) |
| DELETE /members/{memberId} | `memberId` 음수 (`-1`) | 컨트롤러 진입 → service NOT_FOUND (현 정책은 별도 path validation 없음) |
| DELETE /members/me | (입력 없음) | — |
| (모든) PathVariable teamId | 비숫자 | 400 (Spring MVC 자동) |

본 도메인은 request body / query param 이 없어 입력 유효성 케이스가 매우 적음. validation 클래스는 path variable 비숫자 케이스 1~2건만 둔다.

---

## 6. SRS 검사기준 ↔ 테스트 매핑 표

| 검사기준 No | 요구사항 ID | 검사 요지 | 닫는 테스트 |
|---|---|---|---|
| 기능 No.X | RE-SF4-04 | LEADER 가 멤버 강퇴, LEADER 본인 강퇴 차단 | T-MEM-KICK-01 · 02 · 03 · 04 · 05 · 07 · 13 · 14 · 15 |
| 기능 No.X | RE-SF4-05 | 멤버가 본인 탈퇴, LEADER 본인 탈퇴 차단 | T-MEM-LEAVE-01 · 02 · 03 · 08 · 09 · 10 |
| 기능 No.X | (팀원 목록 — 시트) | 활성 멤버 조회 | T-MEM-LIST-01 · 02 · 04 · 05 · 06 · 10 |
| 비기능 No.3 | RE-NF-03 | 입력 형식 위반 시 오류 메시지 | §5 매트릭스 (본 도메인 입력 거의 없음) |

비고:
- **RE-NF-06 (동시성)** 은 D-009 / D-020 §20.c 정책으로 회귀 테스트 스코프 외. 코드 락 호출만 단위 (T-MEM-KICK-13 / T-MEM-LEAVE-08) 에서 verify.

---

## 7. 커버 / 스킵 정책

**커버**
- 정상 흐름: list / kick / leave.
- 권한 분기: LEADER 강퇴 권한, 본인 탈퇴 권한, 비-LEADER/비-멤버 차단.
- 비즈니스 제약: LEADER 강퇴 금지 (`CANNOT_REMOVE_LEADER`), LEADER 탈퇴 금지 (`CONFLICT` 오버라이드).
- 자동 마감 역복구 없음 검증 (T-MEM-KICK-02 / T-MEM-LEAVE-02).
- 상태 정합: is_active / left_reason / left_at + count 동시 갱신.
- 보안 경계: 401 / 403 / 404 매트릭스.

**스킵**
- DTO record 자동 메서드.
- Spring Security 프레임워크 내부.
- `GlobalExceptionHandler` 매핑 자체.
- 부하 / 응답 시간 (RE-NF-04 본 스프린트 외).
- **동시성 회귀 통합 테스트** (D-009 / D-020 §20.c).
- 알림 (P-009).

---

## 8. 커버리지 목표

- `study.service.StudyMemberService` line **90% 이상**, branch **85% 이상**.
- `study.controller.StudyMemberController` 메서드별 정상/실패 1개씩.
- `StudyMember.kick` / `leave` / `isLeader`: branch **100%**.

---

## 9. 다음 단계 진입 체크리스트

테스트 코드 작성 직전 확인:

- [ ] `ErrorCode` 에 `CANNOT_REMOVE_LEADER` 추가 (409).
- [ ] `StudyMember` 엔티티에 `kick()` / `leave()` / `isLeader()` 도메인 메서드 + leftAt / leftReason / updatedAt getter 추가.
- [ ] `MemberLeftReason` enum 신설.
- [ ] `Study.decrementMemberCount()` 존재 확인 (없으면 추가).
- [ ] `StudyMemberRepository` 메서드 4건 추가 (`findAllActiveByStudyIdOrderByJoinedAt` / `findByIdAndStudyIdForUpdate` / `findActiveByStudyIdAndUserIdForUpdate` / `findActiveManager`).
- [ ] `MemberSummaryResponse` / `MemberListResponse` DTO 추가.
- [ ] `StudyMemberController` / `StudyMemberService` 신규.
- [ ] 통합 테스트 클래스 분리: `MemberListIntegrationTest` / `MemberKickIntegrationTest` / `MemberLeaveIntegrationTest`. 단위 `StudyMemberServiceTest` / `StudyMemberDomainTest`. 슬라이스 `StudyMemberControllerTest` / `MemberRequestValidationTest`.
- [ ] LEADER user A / MEMBER user B / MEMBER user D / 비-멤버 user C fixture 4종 발급 헬퍼.

이 9개 닫으면 본 문서로 코드 한 줄도 새 결정 없이 깎을 수 있다.
