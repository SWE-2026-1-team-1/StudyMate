# Sprint 4 도메인 A — 신청/수락/거절 클래스 설계

> **Scope.** Sprint 4 D-020 §20.a 의 도메인 A 6개 엔드포인트 (지원/취소/지원자 목록/수락/거절/내 지원 현황) 구현을 위한 클래스 토폴로지 / 메서드 시그니처 / DTO·Entity 필드 / 에러 코드 / 동시성 정책.
>
> **목적.** 본 문서 결과물이 다음 단계(테스트 작성)의 입력. "테스트가 작성 가능하도록 외부 시그니처와 계약" 까지만 픽싱. 내부 구현 디테일(쿼리 본문/예외 변환 순서 등)은 비워둔다.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 작성). 2차: 최평화(클라이언트 흐름 확인).
>
> **전제 결정.** `docs/decisions-log.md` D-003 / D-010 ~ D-020, `docs/claude-context.md` §1~§3. 도메인 B(멤버)·도메인 C(게시판)는 본 문서 범위 밖 (별도 설계 문서).
>
> **본 문서 대상 엔드포인트 (6건).**
> - `POST   /api/studies/{studyId}/applications`                          — 지원
> - `DELETE /api/studies/{studyId}/applications/my`                       — 본인 PENDING 신청 취소
> - `GET    /api/teams/{teamId}/applications`                             — 지원자 목록 (LEADER)
> - `POST   /api/teams/{teamId}/applications/{applicationId}/approve`     — 수락 (LEADER)
> - `POST   /api/teams/{teamId}/applications/{applicationId}/reject`      — 거절 (LEADER)
> - `GET    /api/mypage/applications`                                     — 내 지원 현황
>
> **`teamId` 의미.** D-010 에 따라 `teamId == studyId`. 컨트롤러 진입 직후 동일 변수로 취급.

---

## 1. 패키지 토폴로지

기준 패키지: `com.studymate.application`
공통 패키지: `com.studymate.common` (ErrorCode / GlobalExceptionHandler / ApiErrorResponse — 인증·스터디 도메인과 공유)
참조 패키지: `com.studymate.study` (Study / StudyMember / StudyRole / StudyStatus / 각 Repository)

```
com.studymate.application
├── controller
│   ├── StudyApplicationController        // /api/studies/{studyId}/applications/*
│   ├── TeamApplicationController         // /api/teams/{teamId}/applications/*
│   └── MyPageApplicationController       // /api/mypage/applications
├── service
│   └── ApplicationService                // 6 메서드 단일 진입점
├── domain
│   ├── Application                       // application
│   └── ApplicationStatus                 // enum {PENDING, ACCEPTED, REJECTED}
├── repository
│   └── ApplicationRepository
├── dto
│   ├── request
│   │   ├── ApplicationCreateRequest
│   │   └── ApplicationRejectRequest      // {rejectReason?}
│   └── response
│       ├── ApplicationCreateResponse
│       ├── ApplicationSummaryResponse    // 지원자 목록 카드
│       ├── ApplicationListResponse       // {applications:[], totalCount, page, size}
│       ├── ApplicationApproveResponse
│       ├── ApplicationRejectResponse
│       └── MyApplicationResponse         // 내 지원 현황 row
└── exception
    └── ApplicationException              // BusinessException 의 application 도메인 서브타입
```

`ApplicationException extends BusinessException`. 컨트롤러까지 그대로 던지고 `GlobalExceptionHandler` 가 단일 지점에서 HTTP 응답으로 변환.

`com.studymate.common.exception.ErrorCode` 에 본 도메인 신규 코드 4건 추가 (§9 참조).

**컨트롤러를 3개로 쪼개는 이유.** URL prefix 3종 (`/api/studies/...`, `/api/teams/...`, `/api/mypage/...`) 가 모두 다르고, 각 prefix 가 책임지는 시나리오(지원/팀 운영/마이페이지) 가 다르다. `@RequestMapping` prefix 를 명확히 갖는 컨트롤러 분리로 라우팅 가독성 확보. 비즈니스 로직은 모두 `ApplicationService` 단일 진입점.

---

## 2. 클래스별 책임 + 의존성

### 2.1 StudyApplicationController
- 책임: 지원자 본인 입장의 HTTP 어댑터. `@RequestMapping("/api/studies/{studyId}/applications")`.
- 메서드: `apply`, `cancelMine`.
- 의존: `ApplicationService`.
- 인증: 모든 메서드 필수 (D-015 / D-020 정합).

### 2.2 TeamApplicationController
- 책임: LEADER 입장의 팀 운영 HTTP 어댑터. `@RequestMapping("/api/teams/{teamId}/applications")`.
- 메서드: `list`, `approve`, `reject`.
- 권한: 각 메서드 진입 직후 Service 에서 `study_member.is_active=1 AND study_role.can_approve_application=1` 검증 → 위반 시 `FORBIDDEN`.

### 2.3 MyPageApplicationController
- 책임: 본인 지원 현황 조회. `@RequestMapping("/api/mypage/applications")`.
- 메서드: `listMine`.
- 본 스프린트 시점에는 본 핸들러 1건만 마이페이지 컨트롤러 신설. 향후 마이페이지 도메인 추가 시 합쳐도 무방 — 패키지가 `application` 인 이유는 응답 본문이 신청 도메인 어휘기 때문.

### 2.4 ApplicationService
- 책임: 6개 엔드포인트의 도메인 트랜잭션 조립. D-020 §20.b (자동 마감) / §20.c (락) / §20.e (지원 거절 매트릭스) / §20.f (수락·거절 권한+처리) / §20.h (LEADER 검증) 의 단일 진입점.
- 의존:
  - `ApplicationRepository`
  - `com.studymate.study.repository.StudyRepository`
  - `com.studymate.study.repository.StudyMemberRepository`
  - `com.studymate.user.repository.UserRepository` (지원자 / 신청자 이름 응답용)
- 트랜잭션 경계: 메서드 1건 = 트랜잭션 1건. 조회는 `readOnly=true`.

### 2.5 Repository 계층
- Spring Data JPA. 본 도메인 커스텀 쿼리는 §6.
- `study` row 락은 `StudyRepository.findByIdAndNotDeletedForUpdate` 재사용 (Sprint 3 §6.1 정의).

---

## 3. 데이터 플로우 (시나리오별 컴포넌트 협력)

테스트 시나리오를 그대로 깎아낼 수 있도록 6개 핵심 흐름의 호출 순서를 글로 픽싱. `→` = 호출, `⇡` = 예외 발생.

### 3.1 지원 (`POST /api/studies/{studyId}/applications`)

```
StudyApplicationController.apply(userId, studyId, ApplicationCreateRequest)
  → ApplicationService.apply(userId, studyId, command)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)        // SELECT ... FOR UPDATE
        ⇡ 없으면 NOT_FOUND
    → (status == CLOSED) → STUDY_FULL                                 // D-020 §20.e
    → StudyMemberRepository.existsActiveByStudyIdAndUserId(studyId, userId)
        → true → ALREADY_MEMBER
    → ApplicationRepository.findPending(studyId, userId)
        → 존재 → ALREADY_APPLIED
    → ApplicationRepository.save(new Application(studyId, userId, PENDING, message))
    → (TX 커밋)
  ← ApplicationCreateResponse(applicationId, studyId, status=PENDING, appliedAt)
```

거절 매트릭스 (D-020 §20.e) 전부 본 흐름에서 차단. study row 락이 없으면 두 클라이언트가 동시에 PENDING row 를 생성할 수 있으므로 락 필수.

`REJECTED` 과거 row 가 있는 경우는 별도 row 신규 INSERT 로 재신청 허용 (위 분기에서 자연스럽게 통과).

### 3.2 본인 신청 취소 (`DELETE /api/studies/{studyId}/applications/my`)

```
StudyApplicationController.cancelMine(userId, studyId) → 204
  → ApplicationService.cancelMine(userId, studyId)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)
        ⇡ 없으면 NOT_FOUND
    → ApplicationRepository.findPendingForUpdate(studyId, userId)    // SELECT ... FOR UPDATE
        ⇡ 없으면 NOT_FOUND  (취소할 PENDING 신청이 없음)
    → applicationRepository.delete(found)                            // 물리 삭제. PENDING 만 삭제 가능.
    → (TX 커밋)
```

ACCEPTED/REJECTED row 는 본 핸들러로 손대지 않는다 (D-020 §20.e 의 "처리 종결된 신청은 status 변경 금지" 정합). 활성 멤버 탈퇴는 도메인 B `DELETE /members/me` 책임.

### 3.3 지원자 목록 (`GET /api/teams/{teamId}/applications?page&size`)

```
TeamApplicationController.list(userId, teamId, page, size)
  → ApplicationService.list(userId, studyId=teamId, page, size)
    → (TX 시작, readOnly)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → assertCanApprove(studyId, userId)                              // can_approve_application 검증
        ⇡ 아니면 FORBIDDEN
    → ApplicationRepository.findAllPendingByStudyId(studyId, pageable)
      // 쿼리: WHERE study_id=? AND status='PENDING'  ORDER BY applied_at ASC
    → 각 row 의 applicant 이름 batch fetch (UserRepository.findAllByIdIn)
  ← ApplicationListResponse(applications[], totalCount, page, size)
```

본 스프린트 노출 대상: **`PENDING` 만**. ACCEPTED/REJECTED 처리 결과는 본 엔드포인트 책임 아님 (히스토리는 별도 도메인).

페이징 디폴트 D-015 정합: `page=0, size=20`, `size` 상한 100.

### 3.4 수락 (`POST /api/teams/{teamId}/applications/{applicationId}/approve`)

```
TeamApplicationController.approve(userId, teamId, applicationId)
  → ApplicationService.approve(userId, studyId=teamId, applicationId)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)
        ⇡ 없으면 NOT_FOUND
    → leaderMember = StudyMemberRepository.findActiveApprover(studyId, userId)
        ⇡ 없으면 FORBIDDEN                                            // can_approve_application 보유자만
    → application = ApplicationRepository.findByIdAndStudyIdForUpdate(applicationId, studyId)
        ⇡ 없으면 NOT_FOUND
    → (application.status != PENDING) → INVALID_APPLICATION_STATUS    // D-020 §20.f
    → (study.status != OPEN) → STUDY_FULL                             // 동일 trans 에서 자동 마감 직전이라도 OPEN 확인
    → application.accept(processedByMemberId=leaderMember.id)
    → StudyMemberRepository.save(new StudyMember(studyId, application.applicantId, MEMBER, active))
    → study.incrementMemberCount()
    → if (study.currentMemberCount == study.maxMembers) study.changeStatus(CLOSED)    // D-020 §20.b
    → (TX 커밋)
  ← ApplicationApproveResponse(applicationId, status=ACCEPTED, processedAt, studyStatusAfter)
```

`studyStatusAfter` 는 자동 마감 발생 여부 가시화용. 응답 본문에 포함시켜 클라이언트가 후속 갱신 판단할 수 있게.

### 3.5 거절 (`POST /api/teams/{teamId}/applications/{applicationId}/reject`)

```
TeamApplicationController.reject(userId, teamId, applicationId, ApplicationRejectRequest)
  → ApplicationService.reject(userId, studyId=teamId, applicationId, command)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)
        ⇡ 없으면 NOT_FOUND
    → leaderMember = StudyMemberRepository.findActiveApprover(studyId, userId)
        ⇡ 없으면 FORBIDDEN
    → application = ApplicationRepository.findByIdAndStudyIdForUpdate(applicationId, studyId)
        ⇡ 없으면 NOT_FOUND
    → (application.status != PENDING) → INVALID_APPLICATION_STATUS
    → application.reject(processedByMemberId=leaderMember.id, reason=command.rejectReason)
    → (TX 커밋)
  ← ApplicationRejectResponse(applicationId, status=REJECTED, processedAt)
```

study / 멤버 카운트 변경 없음 (D-020 §20.f).

### 3.6 내 지원 현황 (`GET /api/mypage/applications?page&size`)

```
MyPageApplicationController.listMine(userId, page, size)
  → ApplicationService.listMine(userId, page, size)
    → (TX 시작, readOnly)
    → ApplicationRepository.findAllByApplicantId(userId, pageable)
      // 쿼리: WHERE applicant_id=?  ORDER BY applied_at DESC
    → 각 row 의 study 정보 batch fetch (StudyRepository.findAllByIdIn — is_deleted 무관)
        // 삭제된 스터디라도 본인이 지원했던 사실은 마이페이지에서 보여준다.
  ← MyApplicationListResponse(applications[], totalCount, page, size)
```

상태별 필터링 (?status=PENDING) 은 본 스프린트 미포함. 전체 status 노출. 응답 row 에 `studyStatus` / `studyTitle` 포함.

is_deleted 처리: 본 조회는 *본인이 보유한 히스토리* 이므로 삭제된 스터디 row 도 표시. `studyTitle` 만 노출하고 클릭 시 클라이언트가 별도 처리 (본 스프린트 시점엔 클라이언트가 알아서).

---

## 4. Controller — 메서드 시그니처

### 4.1 StudyApplicationController

`@RestController`, `@RequestMapping("/api/studies/{studyId}/applications")`, `@RequiredArgsConstructor`, `@Validated`.

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApplicationCreateResponse apply(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long studyId,
    @Valid @RequestBody ApplicationCreateRequest request
);

@DeleteMapping("/my")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void cancelMine(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long studyId
);
```

### 4.2 TeamApplicationController

`@RestController`, `@RequestMapping("/api/teams/{teamId}/applications")`.

```java
@GetMapping
public ApplicationListResponse list(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @RequestParam(defaultValue = "0")  @Min(0)             int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100)   int size
);

@PostMapping("/{applicationId}/approve")
public ApplicationApproveResponse approve(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long applicationId
);

@PostMapping("/{applicationId}/reject")
public ApplicationRejectResponse reject(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long applicationId,
    @Valid @RequestBody(required = false) ApplicationRejectRequest request
);
```

`reject` 본문은 옵셔널 (`required=false`). 사유 미기재 거절도 허용.

### 4.3 MyPageApplicationController

`@RestController`, `@RequestMapping("/api/mypage/applications")`.

```java
@GetMapping
public MyApplicationListResponse listMine(
    @AuthenticationPrincipal CustomUserDetails principal,
    @RequestParam(defaultValue = "0")  @Min(0)             int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100)   int size
);
```

`@Valid` 위반 → `GlobalExceptionHandler` 의 `MethodArgumentNotValidException` 분기 → `INVALID_INPUT`.

---

## 5. ApplicationService — public 메서드 시그니처

```java
@Transactional
public ApplicationCreateResponse apply(long userId, long studyId, ApplicationCreateCommand command);

@Transactional
public void cancelMine(long userId, long studyId);

@Transactional(readOnly = true)
public ApplicationListResponse list(long callerUserId, long studyId, int page, int size);

@Transactional
public ApplicationApproveResponse approve(long callerUserId, long studyId, long applicationId);

@Transactional
public ApplicationRejectResponse reject(long callerUserId, long studyId, long applicationId, ApplicationRejectCommand command);

@Transactional(readOnly = true)
public MyApplicationListResponse listMine(long userId, int page, int size);
```

`*Command` = Service 입력 객체 (Controller 가 DTO 변환해 넘김, 도메인 어휘 유지). Sprint 3 와 동일 패턴.

```java
public record ApplicationCreateCommand(String message) {}        // message 옵셔널
public record ApplicationRejectCommand(String rejectReason) {}    // 본문 자체 옵셔널이면 null 로 만들어 전달
```

---

## 6. Repository — 쿼리 메서드 시그니처

### 6.1 ApplicationRepository (`extends JpaRepository<Application, Long>`)

```java
@Query("""
    select a from Application a
    where a.studyId = :studyId
      and a.applicantId = :applicantId
      and a.status = com.studymate.application.domain.ApplicationStatus.PENDING
""")
Optional<Application> findPending(@Param("studyId") long studyId,
                                  @Param("applicantId") long applicantId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select a from Application a
    where a.studyId = :studyId
      and a.applicantId = :applicantId
      and a.status = com.studymate.application.domain.ApplicationStatus.PENDING
""")
Optional<Application> findPendingForUpdate(@Param("studyId") long studyId,
                                           @Param("applicantId") long applicantId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select a from Application a
    where a.id = :id and a.studyId = :studyId
""")
Optional<Application> findByIdAndStudyIdForUpdate(@Param("id") long id,
                                                  @Param("studyId") long studyId);

@Query("""
    select a from Application a
    where a.studyId = :studyId
      and a.status = com.studymate.application.domain.ApplicationStatus.PENDING
    order by a.appliedAt asc
""")
Page<Application> findAllPendingByStudyId(@Param("studyId") long studyId, Pageable pageable);

Page<Application> findAllByApplicantIdOrderByAppliedAtDesc(long applicantId, Pageable pageable);
```

### 6.2 StudyMemberRepository — Sprint 3 정의에 추가

Sprint 3 §6.2 의 기존 메서드 외 본 도메인용 신규:

```java
boolean existsByStudyIdAndUserIdAndIsActiveTrue(long studyId, long userId);

@Query("""
    select sm from StudyMember sm
    join StudyRole r on r.code = sm.roleCode
    where sm.studyId = :studyId
      and sm.userId  = :userId
      and sm.isActive = true
      and r.canApproveApplication = true
""")
Optional<StudyMember> findActiveApprover(@Param("studyId") long studyId,
                                         @Param("userId") long userId);
```

`StudyRole` 엔티티가 본 스프린트에서 신규 매핑 필요 (Sprint 3 에선 미사용). `study_role` 테이블 매핑 클래스로 추가.

### 6.3 StudyRepository — 추가

```java
@Query("select s from Study s where s.id in :ids")
List<Study> findAllByIdIn(@Param("ids") Collection<Long> ids);
// is_deleted 무관 (마이페이지 §3.6).
```

### 6.4 UserRepository — 추가

```java
List<AppUser> findAllByIdIn(Collection<Long> ids);
// 지원자 이름 batch fetch (§3.3).
```

---

## 7. Entity 필드

DB 스키마 (`studymate_schema.sql` §9 application) 매핑. **컬럼명 = DB 그대로**, JSON 어휘는 DTO 매핑에서 변환.

### 7.1 Application (`application`)

| 필드 | 타입 | 컬럼 | 비고 |
|---|---|---|---|
| `id` | `Long` | `id` | PK |
| `studyId` | `long` | `study_id` | FK study.id |
| `applicantId` | `long` | `applicant_id` | FK app_user.id |
| `status` | `ApplicationStatus` | `status` | ENUM, JPA `@Enumerated(STRING)` |
| `message` | `String` | `message` | VARCHAR(500) nullable |
| `appliedAt` | `LocalDateTime` | `applied_at` | DEFAULT NOW |
| `processedAt` | `LocalDateTime` | `processed_at` | nullable |
| `processedByMemberId` | `Long` | `processed_by_member_id` | nullable, study_member.id 참조 |
| `rejectReason` | `String` | `reject_reason` | VARCHAR(500) nullable |
| `createdAt` | `LocalDateTime` | `created_at` | |
| `updatedAt` | `LocalDateTime` | `updated_at` | |

도메인 메서드:
- `void accept(long processedByMemberId)` — status=ACCEPTED, processedAt=now, processedByMemberId=값.
- `void reject(long processedByMemberId, String reason)` — status=REJECTED, processedAt=now, processedByMemberId=값, rejectReason=reason.
- `boolean isPending()` / `boolean isProcessed()` — 가독성용.

### 7.2 ApplicationStatus (enum)

```java
public enum ApplicationStatus { PENDING, ACCEPTED, REJECTED }
```

### 7.3 StudyRole (`study_role`) — 본 스프린트에서 신규 매핑

| 필드 | 타입 | 컬럼 |
|---|---|---|
| `code` | `String` | `code` (PK) |
| `name` | `String` | `name` |
| `sortOrder` | `int` | `sort_order` |
| `canApproveApplication` | `boolean` | `can_approve_application` |
| `canManageMember` | `boolean` | `can_manage_member` |
| `canCreateAttendance` | `boolean` | `can_create_attendance` |
| `canPostNotice` | `boolean` | `can_post_notice` |

본 스프린트는 `canApproveApplication` 만 사용. 도메인 B / C 가 나머지 사용.

`StudyMember.roleCode` 가 `String` 인 채로 두고, 권한 검증은 `StudyMemberRepository.findActiveApprover` 의 JPQL `join` 으로 처리. 엔티티 간 양방향 매핑은 도입하지 않는다 (코드 단순화).

---

## 8. Request / Response DTO

JSON 어휘 = API 명세 시트. Bean Validation 어노테이션 적용.

### 8.1 ApplicationCreateRequest

```java
public record ApplicationCreateRequest(
    @Size(max = 500)   String message      // nullable, 없으면 null
) {}
```

본문 자체가 빈 객체 `{}` 또는 `{"message": null}` 도 허용. message 길이만 제약.

### 8.2 ApplicationRejectRequest

```java
public record ApplicationRejectRequest(
    @Size(max = 500)   String rejectReason
) {}
```

본문 전체 옵셔널 (`@RequestBody(required = false)`). null 입력 또는 빈 객체 모두 정상 거절 처리.

### 8.3 응답 DTO

```java
public record ApplicationCreateResponse(
    long                 applicationId,
    long                 studyId,
    ApplicationStatus    status,
    LocalDateTime        appliedAt
) {}

public record ApplicationSummaryResponse(
    long                 applicationId,
    long                 applicantUserId,
    String               applicantName,
    String               message,
    LocalDateTime        appliedAt
) {}

public record ApplicationListResponse(
    List<ApplicationSummaryResponse> applications,
    long  totalCount,
    int   page,
    int   size
) {}

public record ApplicationApproveResponse(
    long                 applicationId,
    ApplicationStatus    status,            // 항상 ACCEPTED
    LocalDateTime        processedAt,
    StudyStatus          studyStatusAfter    // 자동 마감 발생 시 CLOSED
) {}

public record ApplicationRejectResponse(
    long                 applicationId,
    ApplicationStatus    status,            // 항상 REJECTED
    LocalDateTime        processedAt,
    String               rejectReason       // 입력값 echo (null 가능)
) {}

public record MyApplicationResponse(
    long                 applicationId,
    long                 studyId,
    String               studyTitle,
    StudyStatus          studyStatus,       // 삭제된 스터디는 isDeleted 별도 표시 불필요 (응답에 안 보임)
    ApplicationStatus    status,
    LocalDateTime        appliedAt,
    LocalDateTime        processedAt        // nullable
) {}

public record MyApplicationListResponse(
    List<MyApplicationResponse> applications,
    long  totalCount,
    int   page,
    int   size
) {}
```

`teamId` 필드를 따로 두지 않음 — D-010 정합. 클라이언트가 `studyId` 를 그대로 팀 도메인 API 호출에 사용.

---

## 9. ErrorCode enum

기존 `com.studymate.common.exception.ErrorCode` 에 **4건 추가** (D-020 §20.k 정합).

| 코드 | HTTP | 메시지 (default) | 발생 위치 |
|---|---|---|---|
| `ALREADY_APPLIED`             | 409 | "이미 신청한 스터디입니다." | §3.1 PENDING 중복 |
| `ALREADY_MEMBER`              | 409 | "이미 해당 스터디의 멤버입니다." | §3.1 활성 멤버 |
| `STUDY_FULL`                  | 409 | "모집이 마감되었거나 정원이 가득 찼습니다." | §3.1 CLOSED / §3.4 ACCEPTED 직전 OPEN 아님 |
| `INVALID_APPLICATION_STATUS`  | 409 | "이미 처리된 신청입니다." | §3.4 / §3.5 PENDING 아닌 application 처리 시도 |

기존 도메인의 `NOT_FOUND` / `FORBIDDEN` / `INVALID_INPUT` 재사용. 응답 `code` 문자열은 enum name 그대로 직렬화 (Sprint 3 §9 보류건은 본 도메인에서도 동일 정책 — 구현 단계에 일괄 정리).

---

## 10. 동시성 / 트랜잭션 정책

### 10.1 비관적 락 (PESSIMISTIC_WRITE) 적용 지점

| 메서드 | study row | application row |
|---|---|---|
| `apply`        | ✅ `findByIdAndNotDeletedForUpdate` | — (신규 INSERT) |
| `cancelMine`   | ✅ `findByIdAndNotDeletedForUpdate` | ✅ `findPendingForUpdate` |
| `approve`      | ✅ `findByIdAndNotDeletedForUpdate` | ✅ `findByIdAndStudyIdForUpdate` |
| `reject`       | ✅ `findByIdAndNotDeletedForUpdate` | ✅ `findByIdAndStudyIdForUpdate` |
| `list`         | — (readOnly) | — |
| `listMine`     | — (readOnly) | — |

`study` row 락 사유 (D-020 §20.c):
- 동시 지원 두 건이 PENDING 중복 검사를 모두 통과한 채 INSERT 되는 race 차단.
- 동시 수락 두 건이 `current_member_count + 1 == maxMembers` 분기를 모두 통과해 정원 초과되는 race 차단 (RE-NF-06).
- `current_member_count` UPDATE 와 `status` 자동 마감 UPDATE 가 같은 row 에서 직렬화.

`application` row 락 (`approve`/`reject`/`cancelMine`):
- 두 LEADER 가 동시에 같은 application 을 approve/reject 처리 시도 → 한 쪽이 락 대기 후 status 재확인 → `INVALID_APPLICATION_STATUS`.

### 10.2 트랜잭션 경계

- 메서드 1건 = 트랜잭션 1건.
- `approve` 의 경우 `application UPDATE` + `study_member INSERT` + `study UPDATE` (count + 자동 마감) 전부 한 트랜잭션.
- 조회 (`list` / `listMine`) 는 `readOnly = true`.

### 10.3 DB 마이그레이션

**변경 없음** (D-020 §20.l). 기존 `application` / `study_member` / `study_role` 테이블 그대로 사용.

JPA 신규 매핑만 추가:
- `Application` 엔티티 (§7.1) — 기존 `application` 테이블에 1:1.
- `StudyRole` 엔티티 (§7.3) — 기존 `study_role` 테이블에 1:1. ddl-auto: validate 통과 확인 필요. 컬럼 타입은 §7.3 의 매핑이 스키마(`TINYINT(1)` ↔ `boolean`, `INT UNSIGNED` ↔ `int`) 와 정합.

### 10.4 동시성 회귀 테스트

D-009 / D-020 §20.c 기조 유지: 코드 락만으로 닫는다. 통합 멀티스레드 회귀 테스트 작성하지 않음.

---

## 11. Trade-off Analysis

### 11.1 컨트롤러 3개 분리 vs 단일 ApplicationController
- **채택**: 3개 분리 (Study / Team / MyPage).
- 비용: 클래스 수 증가.
- 대안: 단일 컨트롤러 + 메서드별 full path. `@RequestMapping` 의 prefix 강제가 사라져 라우팅 파악이 메서드 어노테이션마다 풀스캔 필요.
- 근거: URL prefix 가 책임 경계와 정합 (지원자 입장 / 운영자 입장 / 마이페이지). 향후 마이페이지 도메인 확장 시 분리된 컨트롤러로 흡수 자연스러움.

### 11.2 신청 취소 = 물리 삭제 vs status='CANCELLED'
- **채택**: 물리 삭제. PENDING row 만 삭제 가능.
- 비용: 본인이 취소한 신청 기록은 마이페이지에서 사라짐.
- 대안: 신규 status `CANCELLED` 추가. enum / DB 양쪽 변경 + 마이페이지 응답 분기 + LEADER 목록에 노출 안 하기 로직.
- 근거: 시트 명세의 신청 취소 의미가 *"신청한 적 없던 상태로 되돌리기"* 에 가까움. ACCEPTED/REJECTED 처리된 row 는 보존(이건 처리 종결의 의미가 큼). PENDING 은 아직 처리되지 않은 의사 표명이므로 본인이 거둬들이는 게 자연스러움. enum 확장은 도메인 가중치 대비 가치 낮음.

### 11.3 지원자 목록 = PENDING 만 vs 전체 status
- **채택**: PENDING 만.
- 비용: 처리 완료된 신청 히스토리는 본 엔드포인트로 못 봄.
- 대안: 전체 노출 + `?status=` 필터.
- 근거: 시트 명세의 본 엔드포인트 의미가 *"처리 대기 중인 신청"* 에 가까움. 처리 히스토리는 향후 별도 도메인 (운영 통계). 본 스프린트는 최소 책임.

### 11.4 `findActiveApprover` JPQL join vs Java 단 권한 매핑
- **채택**: JPQL join 한 번에 권한 검증.
- 비용: `StudyRole` 엔티티 매핑 도입. Sprint 3 에선 안 썼던 테이블.
- 대안: `StudyMemberRepository.findActiveLeader(studyId)` (Sprint 3 §6.2 기존) + Java 에서 `roleCode.equals("LEADER")` 또는 별도 권한 enum 매핑.
- 근거: D-020 §20.f 는 권한을 `study_role.can_approve_application=1` 로 정의 (역할 코드 자체에 묶지 않음). 향후 `CO_LEADER` / `OPERATOR` 등 다중 권한 보유 역할 추가 시 본 매핑이 그대로 동작. `roleCode == 'LEADER'` 하드코딩보다 권한 모델에 정합.

### 11.5 자동 마감 = approve TX 내부 vs 별도 이벤트
- **채택**: 같은 TX 내부 (D-020 §20.b).
- 비용: approve 메서드 책임이 약간 늘어남.
- 대안: 도메인 이벤트 발행 → 별도 핸들러가 study.status 갱신. 비동기/트랜잭션 외부 갱신 시 inconsistency 위험 (멤버 추가는 됐는데 status 가 OPEN 인 채로 다른 클라이언트가 추가 지원).
- 근거: 같은 study row 에 락 보유 중. 별도 이벤트로 분리할 이유 없음. 트랜잭션 일관성이 단순성·정합성 모두에서 우월.

---

## 12. D-020 처리 결과 (2026-05-27)

| 결정 | 본 문서 반영 위치 |
|---|---|
| D-020 §20.a 도메인 A 6 엔드포인트 | §1 (패키지) / §4 (Controller) / §5 (Service) |
| D-020 §20.b 자동 마감 (정원 도달 시 OPEN → CLOSED) | §3.4 / §11.5 |
| D-020 §20.c 동시성 — study row PESSIMISTIC_WRITE | §10.1 / §10.2 |
| D-020 §20.e 지원 거절 매트릭스 | §3.1 / §9 |
| D-020 §20.f 수락·거절 권한 + 처리 룰 | §3.4 / §3.5 / §6.2 (`findActiveApprover`) |
| D-020 §20.k 신규 ErrorCode 4건 | §9 |
| D-020 §20.l DB 스키마 변경 없음 | §10.3 |

---

## 13. 다음 단계로 넘기는 입력 체크리스트

테스트 작성 (`engineering:testing-strategy` 스킬) 진입 시 본 문서 + 아래 항목 입력:

- [x] 6개 엔드포인트 시그니처 (§4)
- [x] Service public 메서드 시그니처 (§5)
- [x] Repository 쿼리 메서드 시그니처 (§6)
- [x] Entity 필드 (§7) ↔ DB 스키마 매핑
- [x] Request/Response DTO 모양 + 검증 어노테이션 (§8)
- [x] ErrorCode 신규 4건 (§9)
- [x] 동시성/트랜잭션 정책 (§10.1, §10.2)
- [x] DB 마이그레이션 — 없음 (§10.3)
- [ ] 테스트 플랜 (`docs/application-test-plan.md`) — 다음 단계 산출물
- [ ] 도메인 B (멤버 관리) 클래스 설계 — A 구현 완료 후 진입
- [ ] 도메인 C (게시판) 클래스 설계 — B 구현 완료 후 진입

테스트 매핑 후보 ID (SRS 검사기준 ↔ 본 설계):
- RE-SF3-01 (지원) ↔ §3.1 / `POST /api/studies/{studyId}/applications`
- RE-SF3-02 (지원자 조회) ↔ §3.3 / `GET /api/teams/{teamId}/applications`
- RE-SF3-03 (수락/거절 + 정원 도달 시 자동 마감) ↔ §3.4 / §3.5
- RE-NF-06 (동시성 — 정원 초과 수락 차단) ↔ §10.1 (코드 락만, 회귀 테스트 없음)
- 시트 마이페이지 신청 현황 ↔ §3.6 / `GET /api/mypage/applications`
- 시트 신청 취소 ↔ §3.2 / `DELETE /api/studies/{studyId}/applications/my`
