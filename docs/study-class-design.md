# Sprint 3 — 스터디 도메인 클래스 설계

> **Scope.** Sprint 3 스터디 CRUD 5개 엔드포인트 구현을 위한 클래스 토폴로지 / 메서드 시그니처 / DTO·Entity 필드 / 에러 코드 / 동시성 정책.
>
> **목적.** 이 문서의 결과물이 다음 단계(테스트 작성)의 입력이 된다. "테스트가 작성 가능하도록 외부 시그니처와 계약"을 픽싱하는 데까지만. 내부 구현 디테일(쿼리 본문/예외 변환 순서 등) 은 의도적으로 비워둔다.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 작성). 2차: 최평화(클라이언트 흐름 확인).
>
> **전제 결정.** `docs/decisions-log.md` D-003 / D-010 ~ D-015, `docs/claude-context.md` §1~§3.
>
> **본 스프린트 대상 엔드포인트 (5건).**
> - `POST   /api/studies`            — 스터디 개설
> - `GET    /api/studies`            — 모집 중인 스터디 목록 조회
> - `GET    /api/studies/{studyId}`  — 스터디 상세 조회
> - `PATCH  /api/studies/{studyId}`  — 스터디 수정 + 모집 상태 토글 (단일 핸들러)
> - `DELETE /api/studies/{studyId}`  — 스터디 삭제 (soft)

---

## 1. 패키지 토폴로지

기준 패키지: `com.studymate.study`
공통 패키지: `com.studymate.common` (ErrorCode / GlobalExceptionHandler / ApiErrorResponse — 인증 도메인과 공유)

```
com.studymate.study
├── controller
│   └── StudyController
├── service
│   └── StudyService                  // CRUD + 상태 토글 단일 진입점
├── domain
│   ├── Study                         // study
│   ├── StudyMember                   // study_member (LEADER 자동 생성 / 멤버 카운트 조회용)
│   ├── StudyTag                      // study_tag
│   ├── StudyLanguage                 // study_language (D-013 신규)
│   ├── Tag                           // tag (참조용, 기존)
│   ├── StudyStatus                   // enum {OPEN, CLOSED}  (CANCELLED 는 미사용)
│   └── StudyRole                     // enum {LEADER, MEMBER}
├── repository
│   ├── StudyRepository
│   ├── StudyMemberRepository
│   ├── StudyTagRepository
│   ├── StudyLanguageRepository
│   └── TagRepository                  // tag 조회/생성 (없으면 신규)
├── dto
│   ├── request
│   │   ├── StudyCreateRequest
│   │   └── StudyUpdateRequest
│   └── response
│       ├── StudyCreateResponse
│       ├── StudyDetailResponse
│       ├── StudySummaryResponse        // 목록 카드용
│       ├── StudyListResponse           // {studies: [...], totalCount, page, size}
│       └── StudyUpdateResponse
└── exception
    └── StudyException                  // BusinessException 의 study 도메인 서브타입
```

`StudyException extends BusinessException`. 도메인 예외는 컨트롤러까지 그대로 던지고, 기존 `GlobalExceptionHandler` 가 단일 지점에서 HTTP 응답으로 변환.

`com.studymate.common.exception.ErrorCode` 에 본 스프린트 신규 코드 3건 추가 (§9 참조).

---

## 2. 클래스별 책임 + 의존성

### 2.1 StudyController
- 책임: HTTP 입출력 어댑터. Request DTO ↔ Service 호출 ↔ Response DTO. 비즈니스 로직 없음.
- 의존: `StudyService`.
- 라우팅: `@RequestMapping("/api/studies")`.
- 인증: 모든 메서드 인증 필요 (D-015). `@AuthenticationPrincipal CustomUserDetails` 로 호출자 id 주입.

### 2.2 StudyService
- 책임: 5개 엔드포인트의 도메인 트랜잭션 조립. D-012 (생성 시 LEADER row 동시 INSERT) / D-011 (maxMembers 변경 룰) / D-014 (상태 전이) / D-015 (마감 후 수정 허용 필드 분기) 의 단일 진입점.
- 의존: `StudyRepository`, `StudyMemberRepository`, `StudyTagRepository`, `StudyLanguageRepository`, `TagRepository`.
- 트랜잭션 경계: 메서드 1건 = 트랜잭션 1건. `@Transactional` (조회는 `readOnly=true`).

### 2.3 Repository 계층
- Spring Data JPA. 단순 CRUD 외 본 스프린트에서 필요한 커스텀 쿼리는 §6.
- `@Modifying` 쿼리 사용 시 `@Query` 의 affected-rows 반환을 분기에 활용.

---

## 3. 데이터 플로우 (시나리오별 컴포넌트 협력)

테스트 시나리오를 그대로 깎아낼 수 있도록 5개 핵심 흐름의 호출 순서를 글로 픽싱한다. `→` = 호출, `⇡` = 예외 발생.

### 3.1 스터디 개설 (`POST /api/studies`)

```
Controller.create(userId, StudyCreateRequest)
  → Service.create(userId, command)
    → (TX 시작)
    → TagRepository.findOrCreateAll(request.tags)    // 신규 태그는 INSERT, 기존은 그대로
    → StudyRepository.save(new Study(...))            // current_member_count=1, status=OPEN
    → StudyMemberRepository.save(new StudyMember(study, user, LEADER, active))   // D-012
    → StudyTagRepository.saveAll(...)
    → StudyLanguageRepository.saveAll(...)
    → (TX 커밋)
  ← StudyCreateResponse(studyId, title, status, currentMembers=1, maxMembers)
```

검증 (Controller 진입 직후 Bean Validation):
- `title` 길이, `description` 길이, `tags`/`languages` 원소·크기, `maxMembers`/`durationWeeks` 범위, `meetingCycle` 길이 (D-015 의 모든 한계).

⇡ 위반 → `INVALID_INPUT`.

### 3.2 스터디 목록 조회 (`GET /api/studies?page&size`)

```
Controller.list(Pageable)
  → Service.list(pageable)
    → StudyRepository.findAllOpenNotDeleted(pageable)
      // 쿼리: WHERE status='OPEN' AND is_deleted=0  ORDER BY created_at DESC
    → 각 row 의 tag 목록 batch fetch (StudyTagRepository.findAllByStudyIdIn)
  ← StudyListResponse(studies[], totalCount, page, size)
```

페이징 디폴트: `page=0, size=20`, `size` 상한 100 (Controller 단에서 클램프).

### 3.3 스터디 상세 조회 (`GET /api/studies/{studyId}`)

```
Controller.detail(studyId)
  → Service.detail(studyId)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → StudyTagRepository.findAllByStudyId
    → StudyLanguageRepository.findAllByStudyId
    → StudyMemberRepository.findActiveLeader(studyId)    // createdBy 응답용
  ← StudyDetailResponse(...)
```

### 3.4 스터디 수정 + 상태 토글 (`PATCH /api/studies/{studyId}`)

단일 핸들러. 본문에 변경 의도 필드만 담겨 들어옴 (미전송 = 변경 없음, D-015).

```
Controller.update(userId, studyId, StudyUpdateRequest)
  → Service.update(userId, studyId, command)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)   // SELECT ... FOR UPDATE
        ⇡ 없으면 NOT_FOUND
    → assertLeader(studyId, userId)                              // 활성 LEADER 검증
        ⇡ 아니면 FORBIDDEN
    → 분기 1: command.status 변경 요청 있으면
        → assertValidStatusTransition(current, target, currentMemberCount, maxMembers)
            ⇡ CLOSED→OPEN 이고 cur>=max 면 INVALID_STATUS_TRANSITION
        → study.changeStatus(target)
    → 분기 2: status == CLOSED 인 채로 수정 시도
        → command 가 (maxMembers / durationWeeks / meetingCycle) 중 하나라도 포함 → INVALID_STATUS_FOR_UPDATE
    → 분기 3: command.maxMembers 변경 요청 있으면 (D-011)
        ⇡ newMax < currentMemberCount → INVALID_MAX_MEMBERS
        → study.changeMaxMembers(newMax)
    → 분기 4: command 의 나머지 필드 (title / description / tags / languages / durationWeeks / meetingCycle) 부분 적용
        → tags / languages 가 들어오면 기존 row 전체 교체 (delete + insert)
    → (TX 커밋)
  ← StudyUpdateResponse(studyId, title, status, updatedAt)
```

본 핸들러 하나로 시트의 "스터디 수정" + "모집 마감" 두 row 를 모두 커버 (claude-context §1 통합 모델).

### 3.5 스터디 삭제 (`DELETE /api/studies/{studyId}`)

```
Controller.delete(userId, studyId) → 204
  → Service.delete(userId, studyId)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeletedForUpdate(studyId)
        ⇡ 없으면 NOT_FOUND
    → assertLeader(studyId, userId)
        ⇡ 아니면 FORBIDDEN
    → study.markDeleted()    // is_deleted = 1  (soft delete, D-015 보강)
    → (TX 커밋)
```

자식 row (`study_member` / `study_tag` / `study_language`) 는 그대로 보존. 모든 조회/수정 쿼리는 `is_deleted=0` 조건을 기본 깔고 있으므로 사용자 노출에서 자동 제외.

---

## 4. StudyController — 메서드 시그니처

`@RestController`, `@RequestMapping("/api/studies")`, `@RequiredArgsConstructor`, `@Validated`.

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public StudyCreateResponse create(
    @AuthenticationPrincipal CustomUserDetails principal,
    @Valid @RequestBody StudyCreateRequest request
);

@GetMapping
public StudyListResponse list(
    @RequestParam(defaultValue = "0")  @Min(0)  int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
);

@GetMapping("/{studyId}")
public StudyDetailResponse detail(
    @PathVariable long studyId
);

@PatchMapping("/{studyId}")
public StudyUpdateResponse update(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long studyId,
    @Valid @RequestBody StudyUpdateRequest request
);

@DeleteMapping("/{studyId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long studyId
);
```

`@Valid` 위반 → `GlobalExceptionHandler` 의 `MethodArgumentNotValidException` 분기 → `INVALID_INPUT`.

---

## 5. Service — public 메서드 시그니처

### 5.1 StudyService

```java
@Transactional
public StudyCreateResponse create(long userId, StudyCreateCommand command);

@Transactional(readOnly = true)
public StudyListResponse list(int page, int size);

@Transactional(readOnly = true)
public StudyDetailResponse detail(long studyId);

@Transactional
public StudyUpdateResponse update(long userId, long studyId, StudyUpdateCommand command);

@Transactional
public void delete(long userId, long studyId);
```

`*Command` = Service 입력 객체 (Controller 가 DTO 를 변환해 넘김, 도메인 어휘 유지). Request DTO 와 1:1 매핑이라 사실상 같은 모양이지만 패키지 경계를 분리 — Controller 가 Service 입력 형태를 직접 통제하지 못하게.

`StudyUpdateCommand` 는 필드별 `Optional<T>` 보유:
```java
public record StudyUpdateCommand(
    Optional<String>          title,
    Optional<String>          description,
    Optional<List<String>>    tags,
    Optional<List<String>>    languages,
    Optional<Integer>         maxMembers,
    Optional<Integer>         durationWeeks,
    Optional<String>          meetingCycle,
    Optional<StudyStatus>     status
) {}
```
"미전송 = `Optional.empty()`", "명시 = `Optional.of(value)`", "`null` 명시 = 역직렬화 단계에서 거부 → INVALID_INPUT". (역직렬화 어떻게 시킬지는 구현 단계 — Jackson `Optional` 모듈 + custom deserializer 후보.)

---

## 6. Repository — 쿼리 메서드 시그니처

### 6.1 StudyRepository (`extends JpaRepository<Study, Long>`)

```java
Optional<Study> findByIdAndIsDeletedFalse(long id);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Study s where s.id = :id and s.isDeleted = false")
Optional<Study> findByIdAndNotDeletedForUpdate(@Param("id") long id);

@Query("""
    select s from Study s
    where s.isDeleted = false and s.status = com.studymate.study.domain.StudyStatus.OPEN
    order by s.createdAt desc
""")
Page<Study> findAllOpenNotDeleted(Pageable pageable);
```

### 6.2 StudyMemberRepository

```java
Optional<StudyMember> findByStudyIdAndUserIdAndIsActiveTrue(long studyId, long userId);

@Query("""
    select sm from StudyMember sm
    where sm.studyId = :studyId
      and sm.roleCode = 'LEADER'
      and sm.isActive = true
""")
Optional<StudyMember> findActiveLeader(@Param("studyId") long studyId);

boolean existsByStudyIdAndUserIdAndRoleCodeAndIsActiveTrue(long studyId, long userId, String roleCode);
```

### 6.3 StudyTagRepository

```java
List<StudyTag> findAllByStudyId(long studyId);

@Query("select st from StudyTag st where st.studyId in :studyIds")
List<StudyTag> findAllByStudyIdIn(@Param("studyIds") Collection<Long> studyIds);

void deleteAllByStudyId(long studyId);
```

### 6.4 StudyLanguageRepository — 위와 동일한 형태 (`study_language` 대상).

### 6.5 TagRepository

```java
List<Tag> findAllByNameIn(Collection<String> names);
// findOrCreateAll 은 Service 가 위 메서드 + saveAll 로 조합 (UNIQUE(name) 가정).
```

---

## 7. Entity 필드

DB 스키마 (`studymate_schema.sql`) 매핑. **컬럼명 = DB 그대로**, JSON 어휘는 DTO 매핑 단계에서 변환 (D-015 보강).

### 7.1 Study (`study`)

| 필드 | 타입 | 컬럼 | 비고 |
|---|---|---|---|
| `id` | `Long` | `id` | PK |
| `title` | `String` | `title` | VARCHAR(200) NOT NULL |
| `purpose` | `String` | `purpose` | TEXT NOT NULL — JSON `description` |
| `maxMembers` | `int` | `max_members` | TINYINT UNSIGNED |
| `currentMemberCount` | `int` | `current_member_count` | TINYINT UNSIGNED, 서비스 레이어 유지 (D-003) |
| `activityCycle` | `String` | `activity_cycle` | VARCHAR(100) — JSON `meetingCycle` |
| `durationWeeks` | `int` | `duration_weeks` | **신규** (D-013 / §10.3 마이그레이션) |
| `status` | `StudyStatus` | `status` | ENUM, JPA `@Enumerated(STRING)` |
| `isDeleted` | `boolean` | `is_deleted` | TINYINT(1) DEFAULT 0 |
| `createdAt` | `LocalDateTime` | `created_at` | |
| `updatedAt` | `LocalDateTime` | `updated_at` | |

도메인 메서드:
- `void changeStatus(StudyStatus next)`
- `void changeMaxMembers(int next)`
- `void markDeleted()`
- `void rename(String title)` / `void changePurpose(String purpose)` / `void changeActivityCycle(String cycle)` / `void changeDurationWeeks(int weeks)`
- `void incrementMemberCount()` / `void decrementMemberCount()` — 본 스프린트에선 생성 시 1로 박는 것만 사용. 신청 수락/탈퇴 흐름이 5주차에 활용.

### 7.2 StudyMember (`study_member`)
- 본 스프린트는 생성 시 LEADER row INSERT + 조회 두 가지만 사용.
- 필드: `id`, `studyId`, `userId`, `roleCode`(String), `isActive`(boolean), `joinedAt`, `leftAt`, `leftReason`, `createdAt`, `updatedAt`.

### 7.3 StudyTag (`study_tag`)
- 복합 PK `(studyId, tagId)` — `@EmbeddedId`.

### 7.4 StudyLanguage (`study_language`) — **신규**
- 복합 PK `(studyId, languageCode)`. `languageCode VARCHAR(30)`. `study_tag` 와 동일한 패턴.
- 스키마 정의는 §10.3 마이그레이션 노트에 SQL 박음.

### 7.5 Tag (`tag`)
- `id`, `name` (UNIQUE). 본 스프린트는 `findAllByNameIn` + `saveAll` 만 사용.

---

## 8. Request / Response DTO

JSON 어휘 = API 명세 시트. Bean Validation 어노테이션은 D-015 한계 그대로.

### 8.1 StudyCreateRequest

```java
public record StudyCreateRequest(
    @NotBlank @Size(min=2, max=50)        String       title,
    @NotBlank @Size(max=2000)             String       description,
    @NotEmpty @Size(max=10)
        List<@NotBlank @Size(min=1, max=30) String>    tags,
    @NotEmpty @Size(max=10)
        List<@NotBlank @Size(min=1, max=30) String>    languages,
    @NotNull  @Min(2) @Max(50)            Integer      maxMembers,
    @NotNull  @Min(1) @Max(52)            Integer      durationWeeks,
    @NotBlank @Size(max=50)               String       meetingCycle
) {}
```

추가 검증 (Controller / Service 진입 직후): tags / languages 중복 제거 후 사이즈 재검사.

### 8.2 StudyUpdateRequest

`StudyCreateRequest` 와 같은 필드 셋, 단 모든 필드 `Optional<T>` 래핑. 검증은 *값이 들어왔을 때만* 적용 (`@Valid` + Jackson `Optional` deserializer + 커스텀 검증).

```java
public record StudyUpdateRequest(
    Optional<@NotBlank @Size(min=2, max=50)        String>    title,
    Optional<@NotBlank @Size(max=2000)             String>    description,
    Optional<@NotEmpty @Size(max=10)
        List<@NotBlank @Size(min=1, max=30) String>>          tags,
    Optional<@NotEmpty @Size(max=10)
        List<@NotBlank @Size(min=1, max=30) String>>          languages,
    Optional<@Min(2) @Max(50)                      Integer>   maxMembers,
    Optional<@Min(1) @Max(52)                      Integer>   durationWeeks,
    Optional<@NotBlank @Size(max=50)               String>    meetingCycle,
    Optional<                                      StudyStatus> status   // OPEN | CLOSED
) {}
```

본문 전체가 빈 객체 `{}` → 변경 없음 (정상 200, no-op). 명시적 `null` 은 deserializer 단계에서 거부.

### 8.3 응답 DTO

```java
public record StudyCreateResponse(
    long          studyId,
    String        title,
    StudyStatus   status,
    int           currentMembers,
    int           maxMembers
) {}

public record StudyDetailResponse(
    long              studyId,
    String            title,
    String            description,
    List<String>      tags,
    List<String>      languages,
    int               maxMembers,
    int               currentMembers,
    String            meetingCycle,
    int               durationWeeks,
    StudyStatus       status,
    CreatedBy         createdBy,
    LocalDateTime     createdAt
) {
    public record CreatedBy(long userId, String name) {}
}

public record StudySummaryResponse(
    long          studyId,
    String        title,
    List<String>  tags,
    StudyStatus   status,
    int           currentMembers,
    int           maxMembers
) {}

public record StudyListResponse(
    List<StudySummaryResponse> studies,
    long  totalCount,
    int   page,
    int   size
) {}

public record StudyUpdateResponse(
    long          studyId,
    String        title,
    StudyStatus   status,
    LocalDateTime updatedAt
) {}
```

상세/목록 응답에 `teamId` 필요한 자리(클라이언트 호환) 는 추후 별도 결정 시 추가. 본 스프린트는 D-010 에 따라 `studyId` 만 노출.

---

## 9. ErrorCode enum

기존 `com.studymate.common.exception.ErrorCode` 에 **3건 추가**.

| 코드 | HTTP | 메시지 (default) | 발생 위치 |
|---|---|---|---|
| `STUDY_NOT_FOUND`            | 404 | "스터디를 찾을 수 없습니다." | 상세/수정/삭제 조회 실패 |
| `FORBIDDEN_STUDY`            | 403 | "스터디에 대한 권한이 없습니다." | 수정/삭제 시 LEADER 아님 |
| `INVALID_MAX_MEMBERS`        | 409 | "현재 멤버 수보다 작게 줄일 수 없습니다. 먼저 팀원을 강퇴해 주세요." | D-011 |
| `INVALID_STATUS_TRANSITION`  | 409 | "현재 상태에서 요청한 상태로 전환할 수 없습니다." | D-014 (CLOSED→OPEN 인데 정원 도달) |
| `INVALID_STATUS_FOR_UPDATE`  | 409 | "마감된 스터디에서는 변경할 수 없는 항목입니다." | D-015 마감 후 금지 필드 |

> `STUDY_NOT_FOUND` / `FORBIDDEN_STUDY` 는 명세 시트의 `NOT_FOUND` / `FORBIDDEN` 과 의미는 같지만, 다른 도메인 (팀/댓글) 도입 시 코드 충돌을 피하기 위해 도메인 접두. 응답 message 는 시트 표현 유지. ErrorCode enum 추가 시 기존 `CONFLICT` 처럼 도메인 무지칭 코드와 공존.

API 명세 시트는 `NOT_FOUND` / `FORBIDDEN` 으로 적혀 있음 → enum name 만 도메인화하고 응답 `code` 문자열은 `"NOT_FOUND"` / `"FORBIDDEN"` 으로 유지할지, 코드 문자열까지 도메인화할지는 구현 단계에서 한 번 더 본다. **결정 보류 — 일단 enum 자체는 도메인 접두로 짓고, 응답 직렬화 시점에 시트 어휘로 평탄화한다.** (이건 구현 디테일이라 본 설계에서 픽싱 안 해도 됨.)

---

## 10. 동시성 / 트랜잭션 정책

### 10.1 비관적 락 (PESSIMISTIC_WRITE) 적용 지점
- `PATCH /api/studies/{id}`: `findByIdAndNotDeletedForUpdate(id)` — 동일 스터디에 대한 동시 수정/삭제/상태 전이를 직렬화.
- `DELETE /api/studies/{id}`: 위와 동일.

이유: `current_member_count` 변경 (5주차 수락 흐름) 과 `maxMembers` / `status` 변경이 같은 row 를 읽고 쓰므로 inconsistency 방지.

### 10.2 트랜잭션 경계
- 메서드 1건 = 트랜잭션 1건. `create` 의 경우 `study` + `study_member` + `study_tag` + `study_language` 전부 한 트랜잭션 (D-012).
- 조회 (`list` / `detail`) 는 `readOnly = true`.

### 10.3 DB 마이그레이션 (본 스프린트에서 함께 처리)

`studymate_schema.sql` 갱신. 두 항목:

**A) `study.duration_weeks` 컬럼 신규**
```sql
ALTER TABLE study
    ADD COLUMN duration_weeks TINYINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '활동 기간(주) — D-013';
```
DEFAULT 1 은 기존 row 가 있을 경우 대비. 신규 row 는 항상 명시값 INSERT.

**B) `study_language` 테이블 신규**
```sql
CREATE TABLE study_language (
    study_id      BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    language_code VARCHAR(30)     NOT NULL COMMENT '언어 코드 (예: ko/en/zh)',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (study_id, language_code),
    CONSTRAINT fk_study_language_study
        FOREIGN KEY (study_id) REFERENCES study (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='스터디 사용 언어 (D-013)';
```

자식 row 가 부모 `is_deleted` 와 무관하게 보존되도록 본 스프린트에선 cascade 동작에 의존하지 않음 (soft delete). `ON DELETE CASCADE` 는 향후 hard purge 가 필요할 때를 대비한 안전망.

### 10.4 동시성 회귀 테스트
- D-009 와 동일 정책: 동시성 회귀 테스트는 본 스프린트 스코프 제외. 코드 레벨 락으로 방어만 하고, 통합 테스트에서는 단일 스레드 시나리오만 검증.

---

## 11. Trade-off Analysis

### 11.1 단일 PATCH 핸들러 vs 분리 (수정 / 상태 토글)
- **채택**: 단일. 시트 두 row 가 같은 URL+Method 였고, marcus 가 본 세션에서 통합 핸들러 의도 명시.
- 비용: Service 내부 분기 4단계로 복잡. 대신 클라이언트 측 호출 모델 단순.
- 대안: PUT/POST 로 마감/재오픈 전용 엔드포인트 분리. URL/문서가 늘어나는 비용 vs 분기 단순화의 트레이드 — 학교 프로젝트 규모에선 단일 모델이 학습 곡선 짧음.

### 11.2 `purpose` ↔ `description` / `activity_cycle` ↔ `meetingCycle` 컬럼명 미변경
- **채택**: DB 그대로 두고 DTO 매핑에서 변환.
- 비용: 도메인 객체 필드명과 API 어휘가 다르므로 매핑 미스 위험.
- 대안: ALTER 로 컬럼명 통일. 의미 명확하지만 인증 도메인 / 시드 데이터에 영향 (`purpose`/`activity_cycle` 참조하는 곳 없는지 grep 필요) → ROI 낮음. JPA `@Column(name=...)` 한 줄로 충분.

### 11.3 Soft delete vs Hard delete (cascade)
- **채택**: Soft (`is_deleted = 1`). 자식 row 보존.
- 비용: 모든 조회 쿼리에 `is_deleted = 0` 조건 누락 위험 → 리포지토리 메서드명을 `*NotDeleted` 로 강제.
- 대안: Hard + ON DELETE CASCADE. 단순하지만 통계/히스토리/감사 불가, 실수 한 번에 복구 불능. 학교 프로젝트라도 데모 시연 중 우연한 삭제 복구가 가능해야 안심.

### 11.4 `StudyUpdateCommand` 필드별 `Optional`
- **채택**: `Optional<T>` 로 "미전송" 과 "값 명시" 구분.
- 비용: Jackson `Optional` 역직렬화 설정 필요, 테스트 작성 시 매번 `Optional.of(...)`.
- 대안: nullable 필드 + sentinel. null 의미가 모호 (변경 안 함? 값을 null 로?). 본 스프린트 필드는 전부 not-null 비즈니스 필드라 null 의미 부여하면 모델이 거짓말이 됨. Optional 이 의도 가장 명확.

### 11.5 `tags` / `languages` 변경 시 전체 교체 vs delta
- **채택**: 전체 교체 (delete + insert).
- 비용: 같은 태그 그대로 두는 경우에도 row 재생성, `created_at` 재설정.
- 대안: delta 계산. 코드 복잡도 증가 대비 본 스프린트 시점 이점 없음 (태그/언어에 부가 메타데이터 없음).

---

## 12. D-013 / D-014 / D-015 처리 결과 (2026-05-20)

| 결정 | 본 문서 반영 위치 |
|---|---|
| D-013 (`durationWeeks` / `languages` 보강) | §1 (domain: StudyLanguage) / §7.1 (Study.durationWeeks) / §7.4 (StudyLanguage) / §8 (DTO 전반) / §10.3 (DB 마이그레이션) |
| D-014 (OPEN ↔ CLOSED) | §3.4 분기 1 / §9 (INVALID_STATUS_TRANSITION) / §7.1 (StudyStatus enum — CANCELLED 제외) |
| D-015 (디폴트 묶음) | §2.1 (인증 필수) / §3.2 (정렬/페이징) / §3.4 (PATCH 시멘틱·마감 후 분기) / §3.5 (soft delete) / §5.1 (Command Optional) / §8 (검증값) / §9 (INVALID_STATUS_FOR_UPDATE) |

---

## 13. 다음 단계로 넘기는 입력 체크리스트

테스트 작성 (engineering:testing-strategy 스킬) 진입 시 본 문서 + 아래 항목 입력:

- [x] 5개 엔드포인트 시그니처 (§4)
- [x] Service public 메서드 시그니처 (§5)
- [x] Repository 쿼리 메서드 시그니처 (§6)
- [x] Entity 필드 (§7) ↔ DB 스키마 매핑
- [x] Request/Response DTO 모양 + 검증 어노테이션 (§8)
- [x] ErrorCode 신규 3건 (§9)
- [x] 동시성/트랜잭션 정책 (§10.1, §10.2)
- [x] DB 마이그레이션 SQL (§10.3) — 본 스프린트에서 적용
- [ ] 테스트 플랜 (`docs/study-test-plan.md`) — 다음 단계 산출물
- [ ] Swagger 노출 + EC2 배포 — 마지막 단계

테스트 매핑 후보 ID (SRS 검사기준 ↔ 본 설계):
- RE-SF2-01 (개설) ↔ §3.1 / `POST /api/studies`
- RE-SF2-02 (수정 + 마감 후 인원 축소 제한) ↔ §3.4 분기 2/3 + D-011/D-015
- RE-SF2-02a (삭제 + 접근 차단) ↔ §3.5 + 모든 조회 쿼리 `is_deleted=0`
- RE-SF2-03 (목록 + 최신순) ↔ §3.2
- RE-SF2-04 (상세) ↔ §3.3
