# Sprint 4 도메인 A — 신청/수락 테스트 플랜

> **목적.** 다음 단계(테스트 코드 작성)의 입력. `docs/application-class-design.md` 의 외부 시그니처 + SRS RE-SF3-01~03 검사기준 + D-020 결정을 테스트 케이스 ID 단위로 깎아둔다.
>
> **테스트 코드를 미리 쓰지 않는다.** 본 문서가 픽싱하는 것: (1) 어느 컴포넌트에 어느 타입 테스트 어디까지, (2) 각 시나리오의 행위·상태 어서션, (3) SRS 검사기준 매핑.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 코드 작성). 2차: 검사기준/요구사항 추적표 갱신 시점.
>
> **Scope 외 명시.** 부하 · E2E · UI · 보안 침투 · **동시성 회귀 테스트(D-009 / D-020 §20.c 동일 정책)** · 알림(RE-SF3-04, P-009).

---

## 목차

1. 입력 체크리스트
2. 테스트 피라미드 매핑 + 컴포넌트별 전략
3. 도구·픽스처·테스트 더블 결정
4. 시나리오별 테스트 케이스 (application-class-design §3 1:1)
   - 4.1 지원 (`POST /api/studies/{studyId}/applications`)
   - 4.2 본인 신청 취소 (`DELETE .../applications/my`)
   - 4.3 지원자 목록 (`GET /api/teams/{teamId}/applications`)
   - 4.4 수락 (`POST .../approve`)
   - 4.5 거절 (`POST .../reject`)
   - 4.6 내 지원 현황 (`GET /api/mypage/applications`)
5. 입력 유효성 테스트 매트릭스 (RE-NF-03)
6. SRS 검사기준 ↔ 테스트 매핑 표
7. 커버 / 스킵 정책
8. 커버리지 목표
9. 다음 단계 진입 체크리스트

---

## 1. 입력 체크리스트

테스트 코드 작성 시작 전 다음이 모두 픽싱돼 있어야 한다.

- [x] `docs/application-class-design.md` §2 ~ §10 (클래스 책임 / 데이터 플로우 / 컨트롤러·서비스·리포지토리 시그니처 / Entity·DTO / ErrorCode / 동시성 정책)
- [x] `docs/decisions-log.md` D-020 closed (§20.a ~ §20.l)
- [x] SRS RE-SF3-01 / RE-SF3-02 / RE-SF3-03 검사기준
- [x] `studymate_schema.sql` 의 `application` / `study_member` / `study_role` 컬럼 (본 스프린트 마이그레이션 없음 — §10.3)
- [x] D-020 §20.k 정책 — 신규 ErrorCode 4건 (`ALREADY_APPLIED`, `ALREADY_MEMBER`, `STUDY_FULL`, `INVALID_APPLICATION_STATUS`)
- [x] Sprint 3 인프라 (`StudyFixture`, `Clock` 빈, H2 MySQL 모드, `@AuthenticationPrincipal` / `JwtTokenProvider`) 재사용
- [x] `study_role` seed (`LEADER` / `CO_LEADER` / `OPERATOR` / `MEMBER`) 가 테스트 DB 에 들어가 있는지 — 인증/스터디 도메인 테스트 부트스트랩이 이미 INSERT 함

---

## 2. 테스트 피라미드 매핑 + 컴포넌트별 전략

```
            /  통합 (얇게)         \  @SpringBootTest + MockMvc — HTTP 계약 + DB
           /  슬라이스 (적당히)      \  @WebMvcTest, @DataJpaTest
          /  단위 (두텁게)            \  Service 분기 / Entity 도메인 메서드 / DTO Validation
```

| 컴포넌트 | 테스트 타입 | 이유 |
|---|---|---|
| `StudyApplicationController` / `TeamApplicationController` / `MyPageApplicationController` | `@WebMvcTest` 슬라이스 + `MockMvc`, Service mock | Request 매핑 / `@Valid` / HTTP status / JSON 직렬화 / `@AuthenticationPrincipal` 주입 확인. 비즈니스 로직 X. |
| `ApplicationService` | 순수 Mockito 단위 | apply 거절 매트릭스 분기 / approve 의 자동 마감 분기 / 권한 검증 (`findActiveApprover`) / 트랜잭션 내 호출 순서. DB는 mock. |
| Entity 도메인 메서드 (`Application.accept`, `Application.reject`) | 순수 단위 | status·processedAt·processedBy 갱신 정합. |
| Repository | `@DataJpaTest` (H2) | `findPending` · `findPendingForUpdate` · `findByIdAndStudyIdForUpdate` · `findAllPendingByStudyId` 정렬·필터 · `findActiveApprover` JPQL join · `findAllByApplicantIdOrderByAppliedAtDesc`. |
| 신청/수락 전체 시나리오 6종 | `@SpringBootTest` + MockMvc + H2 | 컨트롤러부터 DB 까지 통합. SRS 검사기준 닫는 위치. |

**검증 종류 구분.**
- *동작 어서션*: HTTP status / body JSON / ErrorCode 문자열.
- *상태 어서션*: `application.status` / `application.processed_at` / `application.processed_by_member_id` / `application.reject_reason` / `study.current_member_count` / `study.status` (자동 마감) / `study_member` 신규 row.
- *상호작용 어서션*: Service 내부 호출 순서 — `studyRepository.findByIdAndNotDeletedForUpdate` → `findActiveApprover` → `applicationRepository.findByIdAndStudyIdForUpdate` → `application.accept(...)` → `studyMemberRepository.save(MEMBER)` → `study.incrementMemberCount()` → 자동 마감 분기.

세 종류를 매 케이스에 명시 (§4 표 "검증 포인트" 칸).

---

## 3. 도구·픽스처·테스트 더블 결정

| 항목 | 결정 | 비고 |
|---|---|---|
| 통합/슬라이스 DB | **H2** (`MODE=MYSQL`) | Sprint 2·3 동일. `@Lock(PESSIMISTIC_WRITE)` 는 H2 에서 noop 에 가깝지만 D-009 / D-020 §20.c 동일 정책으로 동시성 회귀 테스트 제외 → 영향 없음. |
| 시간 | `Clock` 빈 주입. 테스트는 `Clock.fixed` 또는 `MutableClock`. | `appliedAt DESC` (마이페이지) / `appliedAt ASC` (지원자 목록) 정렬 검증에 사용. `processedAt` 비교에도 활용. |
| 인증 컨텍스트 | Sprint 2 의 `JwtTokenProvider` + `UserFixture`. LEADER user A / 일반 user B / 비-멤버 user C 3종 fixture. | LEADER 권한 분기 + 본인/타인 분기 검증. |
| 픽스처 | `ApplicationFixture.pending(studyId, applicantId)` / `ApplicationFixture.accepted(...)` / `ApplicationFixture.rejected(...)`. `StudyFixture.openStudy(...)` / `closedStudy(...)` 재사용. `StudyMemberFixture.leader(studyId, userId)` / `member(studyId, userId)`. | 모든 통합 테스트 공유. |
| 통합 테스트 트랜잭션 | `@Transactional` 사용 (롤백). 컨트롤러가 자체 트랜잭션 커밋한 상태를 어서션할 때는 `entityManager.clear()` 후 `findById`. | Sprint 3 동일 패턴 (H2 stale read 이슈는 JPA repository 어서션으로 해결). |
| `study_role` 시드 | 통합 테스트 컨텍스트 부트스트랩 시점에 `LEADER` (`can_approve_application=1`) / `MEMBER` (`can_approve_application=0`) 최소 2건 INSERT 보장. | `findActiveApprover` JPQL join 이 동작하려면 row 존재 필수. |

---

## 4. 시나리오별 테스트 케이스

각 표의 "ID" 는 테스트 메서드 명에 그대로 박는다. 형식: `T-APP-<엔드포인트약자>-<번호>`.
"레이어": `U` = 단위, `WMVC` = `@WebMvcTest`, `DJ` = `@DataJpaTest`, `SBT` = `@SpringBootTest` 통합.

### 4.1 지원 (`POST /api/studies/{studyId}/applications`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-APP-CREATE-01 | SBT | 사전: OPEN study (LEADER=A, cur=1, max=5). user B 로그인. 본문 `{"message":"하고싶어요"}`. | 201 `{applicationId, studyId, status:"PENDING", appliedAt}`. `application` row 1건 (status=PENDING, applicant=B, message 저장). | 동작/상태 |
| T-APP-CREATE-02 | SBT | 위와 동일, 본문 `{}` (message 미기재) | 201. `application.message=null`. | 동작/상태 |
| T-APP-CREATE-03 | SBT | 위와 동일, 본문 자체 생략 (`@RequestBody`) | 400 `INVALID_INPUT` (`@RequestBody` required=true 라면). **확정**: 본 엔드포인트는 body required=true, `{}` 도 명시. → 본 케이스는 400. | 동작 |
| T-APP-CREATE-04 | SBT | 미존재 studyId | 404 `NOT_FOUND` | 동작 |
| T-APP-CREATE-05 | SBT | soft-deleted study | 404 `NOT_FOUND` | 동작 |
| T-APP-CREATE-06 | SBT | 사전: CLOSED study | 409 `STUDY_FULL`. `application` row 미생성. | 동작/상태 |
| T-APP-CREATE-07 | SBT | 사전: user B 가 이미 활성 멤버 (`study_member.is_active=1`). | 409 `ALREADY_MEMBER`. | 동작/상태 |
| T-APP-CREATE-08 | SBT | 사전: user B 가 PENDING 신청 보유 중. | 409 `ALREADY_APPLIED`. application row 추가 INSERT 없음. | 동작/상태 |
| T-APP-CREATE-09 | SBT | 사전: user B 가 과거 REJECTED 신청 보유. 현재 활성 멤버 아님. | 201. **신규 row INSERT** (재신청 허용 — D-020 §20.e). | 동작/상태 |
| T-APP-CREATE-10 | SBT | 사전: user B 가 과거 ACCEPTED → 탈퇴 (`is_active=0`) 상태. | 201. (활성 멤버 아니므로 재지원 허용.) | 동작/상태 |
| T-APP-CREATE-11 | SBT | LEADER 본인 (user A) 이 자기 스터디 지원 시도 | 409 `ALREADY_MEMBER` (A 가 활성 LEADER). | 동작 |
| T-APP-CREATE-12 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-APP-CREATE-13 | WMVC | `{"message": "a".repeat(501)}` | 400 `INVALID_INPUT` | 동작 |
| T-APP-CREATE-14 | U (Service) | mock 리포지토리. apply 호출 → `studyRepository.findByIdAndNotDeletedForUpdate` (락) → CLOSED 분기 → `existsActiveByStudyIdAndUserId` → `findPending` → `save` 순서 | Mockito `InOrder`. 락 메서드 호출 검증. | 상호작용 |
| T-APP-CREATE-15 | U (Service) | mock. CLOSED study 입력 시 `existsActive` / `findPending` / `save` 모두 호출되지 않음 (fail-fast). | Mockito verifyNoMoreInteractions | 상호작용 |

### 4.2 본인 신청 취소 (`DELETE /api/studies/{studyId}/applications/my`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-APP-CANCEL-01 | SBT | 사전: user B 가 PENDING 신청 보유. user B 호출. | 204. `application` row **물리 삭제**. | 동작/상태 |
| T-APP-CANCEL-02 | SBT | 사전: PENDING 신청 없음. | 404 `NOT_FOUND` | 동작 |
| T-APP-CANCEL-03 | SBT | 사전: user B 가 ACCEPTED row 만 보유 (PENDING 없음). | 404 `NOT_FOUND`. ACCEPTED row 무변경. | 동작/상태 |
| T-APP-CANCEL-04 | SBT | 사전: user B 가 REJECTED row 만 보유. | 404 `NOT_FOUND`. REJECTED row 무변경. | 동작/상태 |
| T-APP-CANCEL-05 | SBT | 미존재 studyId | 404 `NOT_FOUND` | 동작 |
| T-APP-CANCEL-06 | SBT | soft-deleted study | 404 `NOT_FOUND` | 동작 |
| T-APP-CANCEL-07 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-APP-CANCEL-08 | U (Service) | cancelMine 호출 시 `findByIdAndNotDeletedForUpdate` (락) → `findPendingForUpdate` (락) → `delete` 순서 | Mockito `InOrder` | 상호작용 |

### 4.3 지원자 목록 (`GET /api/teams/{teamId}/applications`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-APP-LIST-01 | SBT | 사전: study (LEADER=A). PENDING 3건 (appliedAt 다름) + ACCEPTED 1건 + REJECTED 1건. user A 호출. | 200. `applications.length=3` (PENDING 만). `appliedAt ASC` 정렬. `totalCount=3`. 각 row 에 `applicantUserId`, `applicantName`, `message`, `appliedAt`. | 동작/상태 |
| T-APP-LIST-02 | SBT | 사전: PENDING 25건. `page=0&size=10` | length=10, totalCount=25 | 동작 |
| T-APP-LIST-03 | SBT | `page=2&size=10` (위 사전) | length=5, page=2 | 동작 |
| T-APP-LIST-04 | SBT | 사전: PENDING 0건 | 200, `applications=[]`, `totalCount=0` | 동작 |
| T-APP-LIST-05 | SBT | 비-LEADER user B (활성 일반 멤버) 호출 | 403 `FORBIDDEN` (`can_approve_application=0`) | 동작 |
| T-APP-LIST-06 | SBT | 비-멤버 user C 호출 | 403 `FORBIDDEN` | 동작 |
| T-APP-LIST-07 | SBT | LEADER A 가 탈퇴 후 호출 (`is_active=0`) | 403 `FORBIDDEN` (현재 활성 LEADER 아님) — 비현실적 시나리오지만 코드 가드 검증 | 동작 |
| T-APP-LIST-08 | SBT | 미존재 teamId | 404 `NOT_FOUND` | 동작 |
| T-APP-LIST-09 | SBT | soft-deleted study | 404 `NOT_FOUND` | 동작 |
| T-APP-LIST-10 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-APP-LIST-11 | WMVC | `page=-1` / `size=0` / `size=101` | 400 `INVALID_INPUT` | 동작 |
| T-APP-LIST-12 | DJ | `findAllPendingByStudyId` | PENDING 만 / `appliedAt ASC` / 페이징 검증 | 동작 |
| T-APP-LIST-13 | DJ | `findActiveApprover` | LEADER (can_approve=1) 만 리턴, MEMBER 는 empty. is_active=0 도 empty. | 동작 |

### 4.4 수락 (`POST /api/teams/{teamId}/applications/{applicationId}/approve`)

자동 마감 분기가 핵심.

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-APP-APPROVE-01 | SBT | OPEN study (cur=1, max=5), B의 PENDING 신청. LEADER A 호출. | 200 `{applicationId, status:"ACCEPTED", processedAt, studyStatusAfter:"OPEN"}`. `application.status=ACCEPTED`, `processed_by_member_id=A의 study_member.id`, `processed_at` 채워짐. `study_member` 신규 row (user=B, role=MEMBER, is_active=1). `study.current_member_count=2`, `study.status=OPEN` (마감 미발생). | 동작/상태 |
| T-APP-APPROVE-02 | SBT | OPEN study (cur=4, max=5), B의 PENDING. LEADER 수락. | 200. `studyStatusAfter:"CLOSED"`. `study.current_member_count=5`, `study.status=CLOSED` (자동 마감). | 동작/상태 (D-020 §20.b) |
| T-APP-APPROVE-03 | SBT | OPEN study (cur=1, max=2), B의 PENDING. LEADER 수락. | 200. `studyStatusAfter:"CLOSED"`, count=2. (경계: 정확히 max 도달) | 동작/상태 |
| T-APP-APPROVE-04 | SBT | application 이 이미 ACCEPTED | 409 `INVALID_APPLICATION_STATUS`. 상태 무변경. | 동작/상태 |
| T-APP-APPROVE-05 | SBT | application 이 이미 REJECTED | 409 `INVALID_APPLICATION_STATUS`. | 동작/상태 |
| T-APP-APPROVE-06 | SBT | CLOSED study (수동 마감), PENDING application 수락 시도 | 409 `STUDY_FULL` (§3.4 의 OPEN 사전체크). | 동작/상태 |
| T-APP-APPROVE-07 | SBT | 비-LEADER user B 가 다른 신청 approve 시도 | 403 `FORBIDDEN` | 동작/상태 |
| T-APP-APPROVE-08 | SBT | 비-멤버 user C 호출 | 403 `FORBIDDEN` | 동작 |
| T-APP-APPROVE-09 | SBT | 미존재 applicationId | 404 `NOT_FOUND` | 동작 |
| T-APP-APPROVE-10 | SBT | application.studyId 가 path 의 teamId 와 다름 | 404 `NOT_FOUND` (URL-resource 정합 — 다른 스터디 신청 처리 차단) | 동작 |
| T-APP-APPROVE-11 | SBT | 미존재 teamId | 404 `NOT_FOUND` | 동작 |
| T-APP-APPROVE-12 | SBT | soft-deleted study | 404 `NOT_FOUND` | 동작 |
| T-APP-APPROVE-13 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-APP-APPROVE-14 | U (Service) | approve 호출 순서: `findByIdAndNotDeletedForUpdate` → `findActiveApprover` → `findByIdAndStudyIdForUpdate` → PENDING 분기 → OPEN 분기 → `application.accept(leaderMemberId)` → `studyMemberRepository.save(MEMBER)` → `study.incrementMemberCount` → 자동 마감 분기 | Mockito `InOrder` | 상호작용 |
| T-APP-APPROVE-15 | U (Service) | mock. cur=4, max=5. 수락 호출 후 `study.changeStatus(CLOSED)` 호출 검증 | Mockito verify | 상호작용 |
| T-APP-APPROVE-16 | U (Service) | mock. cur=1, max=5. 수락 후 `study.changeStatus` 호출되지 않음 | Mockito `verify(study, never()).changeStatus(any())` | 상호작용 |

### 4.5 거절 (`POST /api/teams/{teamId}/applications/{applicationId}/reject`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-APP-REJECT-01 | SBT | OPEN study, B의 PENDING. LEADER 가 `{"rejectReason":"기존 멤버와 방향 불일치"}` 거절. | 200 `{applicationId, status:"REJECTED", processedAt, rejectReason}`. `application.status=REJECTED`, `processed_by_member_id`, `processed_at`, `reject_reason` 저장. `study.current_member_count` 무변경. `study.status` 무변경. | 동작/상태 |
| T-APP-REJECT-02 | SBT | LEADER 가 본문 없이 거절 (`@RequestBody(required=false)`). | 200. `reject_reason=null`. | 동작/상태 |
| T-APP-REJECT-03 | SBT | LEADER 가 `{}` 본문으로 거절. | 200. `reject_reason=null`. | 동작/상태 |
| T-APP-REJECT-04 | SBT | application 이 이미 ACCEPTED | 409 `INVALID_APPLICATION_STATUS` | 동작/상태 |
| T-APP-REJECT-05 | SBT | application 이 이미 REJECTED | 409 `INVALID_APPLICATION_STATUS` | 동작/상태 |
| T-APP-REJECT-06 | SBT | 비-LEADER user B 호출 | 403 `FORBIDDEN` | 동작/상태 |
| T-APP-REJECT-07 | SBT | 미존재 applicationId | 404 `NOT_FOUND` | 동작 |
| T-APP-REJECT-08 | SBT | application.studyId ≠ teamId | 404 `NOT_FOUND` | 동작 |
| T-APP-REJECT-09 | SBT | 미존재 / soft-deleted study | 404 `NOT_FOUND` | 동작 |
| T-APP-REJECT-10 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-APP-REJECT-11 | WMVC | `{"rejectReason": "a".repeat(501)}` | 400 `INVALID_INPUT` | 동작 |
| T-APP-REJECT-12 | U (Service) | 거절 호출 순서: `findByIdAndNotDeletedForUpdate` → `findActiveApprover` → `findByIdAndStudyIdForUpdate` → PENDING 분기 → `application.reject(leaderMemberId, reason)`. `studyMemberRepository.save` / `study.*` 호출 없음. | Mockito `InOrder` + `verifyNoInteractions(studyMemberRepository)` | 상호작용 |

### 4.6 내 지원 현황 (`GET /api/mypage/applications`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-APP-MYLIST-01 | SBT | 사전: user B 의 application 5건 (PENDING 2 + ACCEPTED 1 + REJECTED 2, appliedAt 다양). | 200. `applications.length=5`, `appliedAt DESC` 정렬, 각 row 에 `studyId`, `studyTitle`, `studyStatus`, `status`, `appliedAt`, `processedAt(nullable)`. | 동작/상태 |
| T-APP-MYLIST-02 | SBT | 사전: user B 의 application 0건 | 200, 빈 배열 | 동작 |
| T-APP-MYLIST-03 | SBT | 사전: 본인의 application 1건 + 타 user 의 application 다수 | 본인 것만 노출 | 동작/상태 |
| T-APP-MYLIST-04 | SBT | 사전: 본인의 application 중 1건이 soft-deleted study | 응답에 포함됨 (마이페이지는 본인 히스토리 보존, §3.6) | 동작/상태 |
| T-APP-MYLIST-05 | SBT | 페이징 `page=0&size=10`, 사전 25건 | length=10, totalCount=25 | 동작 |
| T-APP-MYLIST-06 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-APP-MYLIST-07 | WMVC | `page=-1` / `size=101` | 400 `INVALID_INPUT` | 동작 |
| T-APP-MYLIST-08 | DJ | `findAllByApplicantIdOrderByAppliedAtDesc` | 본인 row 만 / `appliedAt DESC` / 페이징 | 동작 |

---

## 5. 입력 유효성 테스트 매트릭스 (RE-NF-03)

`@WebMvcTest` + `MockMvc` 슬라이스에서 `ApplicationRequestValidationTest` 한 클래스로 묶는다.

| Request DTO | 필드 | 케이스 | 기대 |
|---|---|---|---|
| `ApplicationCreateRequest` | message | null / 빈 / 500자(경계) / 501자 | null·빈·500 통과; 501 → 400 |
| `ApplicationCreateRequest` | body | `{}` (빈 객체) | 통과 (message=null) |
| `ApplicationCreateRequest` | body | 본문 자체 생략 | 400 (`@RequestBody` required) |
| `ApplicationRejectRequest` | rejectReason | null / 빈 / 500자(경계) / 501자 | null·빈·500 통과; 501 → 400 |
| `ApplicationRejectRequest` | body | 본문 생략 / `{}` | 통과 (rejectReason=null, `required=false`) |
| (모든) PathVariable | studyId / teamId / applicationId | 음수 / 비숫자 | 비숫자 → 400 (Spring MVC 자동) |
| (모든) 페이징 | page | -1 | 400 `INVALID_INPUT` |
| (모든) 페이징 | size | 0 / 101 | 400 `INVALID_INPUT` |

응답 JSON shape: `{ "code": "INVALID_INPUT", "message": "<첫 위반 메시지>" }`. Sprint 3 `ApiErrorResponse` 어서션 헬퍼 재사용.

---

## 6. SRS 검사기준 ↔ 테스트 매핑 표

| 검사기준 No | 요구사항 ID | 검사 요지 | 닫는 테스트 |
|---|---|---|---|
| 기능 No.X | RE-SF3-01 | 인증된 학생이 모집 중 스터디에 지원 가능, 중복 / 폐쇄 시 거절 | T-APP-CREATE-01 ~ 11 (정상·거절 매트릭스) + T-APP-CANCEL-01 ~ 04 |
| 기능 No.X | RE-SF3-02 | LEADER 가 지원자 목록 조회, 비-LEADER 차단 | T-APP-LIST-01·04·05·06·07·12·13 |
| 기능 No.X | RE-SF3-03 | LEADER 가 신청 수락/거절. 수락 시 멤버 추가 + 정원 도달 시 자동 마감. | T-APP-APPROVE-01·02·03·04·06·15·16 + T-APP-REJECT-01·04·12 |
| 기능 No.X | (마이페이지 신청 현황 — 시트) | 본인 지원 이력 조회 | T-APP-MYLIST-01·03·04·05 |
| 비기능 No.3 | RE-NF-03 | 입력 형식 위반 시 오류 메시지 | §5 매트릭스 전부 |

비고:
- **RE-NF-06 (동시성)** 은 D-009 / D-020 §20.c 동일 정책으로 본 스프린트 회귀 테스트 스코프 외. 코드 레벨 방어(`PESSIMISTIC_WRITE` 락 메서드 호출, 단일 트랜잭션) 는 단위 테스트(T-APP-CREATE-14 / T-APP-CANCEL-08 / T-APP-APPROVE-14 / T-APP-REJECT-12) 에서 *호출되는지* 만 verify.
- **RE-SF3-04 (지원/수락 알림)** 은 P-009 로 본 스프린트 제외.
- 검사기준 번호는 SRS v2.x 검사기준 표에 맞춰 채워넣을 것 (추적표 갱신 시점 일괄 매핑).

---

## 7. 커버 / 스킵 정책

**커버**
- 비즈니스 핵심 경로: apply → list → approve → reject → mylist → cancel 의 정상 흐름과 실패 분기.
- 에러 핸들링: 본 도메인 4 신규 ErrorCode (`ALREADY_APPLIED`, `ALREADY_MEMBER`, `STUDY_FULL`, `INVALID_APPLICATION_STATUS`) + 재사용 `NOT_FOUND` / `FORBIDDEN` / `INVALID_INPUT` / `UNAUTHORIZED` 각각 최소 1개.
- 엣지 케이스: cur+1 == max 경계 (자동 마감 발생), cur+1 < max (자동 마감 미발생), 재신청 (REJECTED 이후 신규 row), URL teamId ≠ application.studyId.
- 보안 경계: 비-LEADER 의 list/approve/reject 차단, 본인 외 application 조작 차단, 인증 없는 모든 엔드포인트 401.
- 데이터 무결성: PENDING 만 노출/처리, ACCEPTED/REJECTED 의 status 재변경 차단, 수락 시 멤버 row + count + 자동 마감 원자성.

**스킵**
- DTO record 자동 생성 메서드.
- Spring Security 프레임워크 내부.
- Pageable 내부 동작.
- `GlobalExceptionHandler` 매핑 자체 (인증·스터디 도메인에서 이미 깎음 — 본 스프린트는 status + code 만 어서션).
- 부하 / 응답 시간 (RE-NF-04 본 스프린트 외).
- **동시성 회귀 통합 테스트** (D-009 / D-020 §20.c).
- 알림 도메인 (P-009).
- Swagger 문서 생성/노출 검증 (별도 단계).

---

## 8. 커버리지 목표

- `application.service` 패키지 line coverage **90% 이상**, branch coverage **85% 이상** (apply 거절 매트릭스 + approve 자동 마감 분기 + reject 처리 분기 모두 닫혀야 함).
- `application.controller` 패키지: 메서드별 정상/실패 1개씩 닫혔으면 충분.
- `application.domain` (Entity 도메인 메서드 `Application.accept`/`reject`): branch coverage **100%**.
- 미달 시 강제 머지 차단 없음. 1차 게이트는 §4 ID 가 코드로 1:1 옮겨졌는지.

---

## 9. 다음 단계 진입 체크리스트

테스트 코드 작성 직전 확인:

- [ ] `com.studymate.common.exception.ErrorCode` 에 신규 4건 추가 (`ALREADY_APPLIED` / `ALREADY_MEMBER` / `STUDY_FULL` / `INVALID_APPLICATION_STATUS`)
- [ ] `Application` 엔티티 + `ApplicationStatus` enum 매핑 (ddl-auto validate 통과)
- [ ] `StudyRole` 엔티티 신규 매핑 (Sprint 3 미사용 테이블 → 본 스프린트에서 매핑) — H2 / MySQL 양쪽 validate 통과 확인
- [ ] `ApplicationRepository` 5개 메서드 + `StudyMemberRepository.findActiveApprover` 추가
- [ ] `StudyRepository.findAllByIdIn` / `UserRepository.findAllByIdIn` 추가
- [ ] `ApplicationFixture` / `StudyMemberFixture.leader/member` 정적 헬퍼 작성
- [ ] §4 ID 가 테스트 메서드명에 1:1 매핑되도록 패키지 구조 결정 (예: `application/ApplicationCreateIntegrationTest.java`, `application/ApplicationApproveIntegrationTest.java`, `application/ApplicationRequestValidationTest.java`)
- [ ] LEADER user A / 일반 멤버 user B / 비-멤버 user C fixture 3종 발급 헬퍼

이 8개를 닫으면 본 문서 작성 시점부터 코드 한 줄도 새로 결정할 일 없이 깎을 수 있다. 결정 필요 항목이 나타나면 `docs/decisions-log.md` 등록 후 본 문서 업데이트.
