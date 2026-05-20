# Sprint 3 — 스터디 도메인 테스트 플랜

> **목적.** 다음 단계(테스트 코드 작성) 의 입력. `docs/study-class-design.md` 의 외부 시그니처 + SRS RE-SF2-01~04 검사기준 + D-010 ~ D-015 결정을 테스트 케이스 ID 단위로 깎아둔다.
>
> **테스트 코드를 미리 쓰지 않는다.** 본 문서가 픽싱하는 것은 (1) 어느 컴포넌트에 어느 타입의 테스트를 어디까지 쓸지, (2) 각 시나리오에서 검증할 행위·상태 어서션, (3) SRS 검사기준이 어떤 테스트로 닫히는지 매핑.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 코드 작성). 2차: 검사기준/요구사항 추적표 갱신 시점의 본인 또는 팀.
>
> **Scope 외 명시.** 부하 테스트 · E2E · UI · 보안 침투 · **동시성 회귀 테스트(D-009 동일 정책)**. 본 스프린트 검증 목적은 *명세대로 동작하는지* 뿐.

---

## 목차

1. 입력 체크리스트
2. 테스트 피라미드 매핑 + 컴포넌트별 전략
3. 도구·픽스처·테스트 더블 결정
4. 시나리오별 테스트 케이스 (study-class-design §3 1:1)
   - 4.1 스터디 개설 (`POST /api/studies`)
   - 4.2 스터디 목록 조회 (`GET /api/studies`)
   - 4.3 스터디 상세 조회 (`GET /api/studies/{studyId}`)
   - 4.4 스터디 수정 + 상태 토글 (`PATCH /api/studies/{studyId}`)
   - 4.5 스터디 삭제 (`DELETE /api/studies/{studyId}`)
5. 입력 유효성 테스트 매트릭스 (RE-NF-03)
6. SRS 검사기준 ↔ 테스트 매핑 표
7. 커버 / 스킵 정책
8. 커버리지 목표
9. 다음 단계 진입 체크리스트

---

## 1. 입력 체크리스트

테스트 코드 작성 시작 전 다음이 모두 픽싱돼 있어야 한다.

- [x] `docs/study-class-design.md` §2 ~ §10 (클래스 책임 / 데이터 플로우 / 컨트롤러·서비스·리포지토리 시그니처 / Entity·DTO / ErrorCode / 동시성·DB 마이그레이션)
- [x] `docs/decisions-log.md` D-003, D-010 ~ D-015 모두 closed
- [x] SRS RE-SF2-01 / RE-SF2-02 / RE-SF2-03 / RE-SF2-04 검사기준
- [x] `studymate_schema.sql` 의 `study` / `study_member` / `study_tag` / `tag` 컬럼 + §10.3 마이그레이션 (study.duration_weeks 신규, study_language 신규)
- [x] D-015 정책 상수 — `title∈[2,50]`, `description≤2000`, `tags|languages` 각 `[1,30]`·최대 10개·중복 제거, `maxMembers∈[2,50]`, `durationWeeks∈[1,52]`, `meetingCycle≤50`, page=0/size=20/size 상한 100
- [x] 인증 도메인(Sprint 2) 테스트 인프라 (`@SpringBootTest`, H2 MySQL 모드, `JwtTokenProvider`, `CustomUserDetails`, `FakeEmailSender` 등) 이미 존재

---

## 2. 테스트 피라미드 매핑 + 컴포넌트별 전략

```
            /  통합 (얇게)         \  @SpringBootTest + MockMvc — HTTP 계약 + DB
           /  슬라이스 (적당히)      \  @WebMvcTest, @DataJpaTest
          /  단위 (두텁게)            \  Service 분기 / Entity 도메인 메서드 / DTO Validation
```

| 컴포넌트 | 테스트 타입 | 이유 |
|---|---|---|
| `StudyController` | `@WebMvcTest` 슬라이스 + `MockMvc`, Service mock | Request 매핑 / `@Valid` / HTTP status / JSON 직렬화 / `@AuthenticationPrincipal` 주입 확인. 비즈니스 로직 X. |
| `StudyService` | 순수 Mockito 단위 | PATCH 4분기 (status / CLOSED 제한 / maxMembers / 일반 필드) · LEADER 검증 · 트랜잭션 내 호출 순서 검증. DB는 mock. |
| Entity 도메인 메서드 (`Study.changeStatus`, `changeMaxMembers`, `markDeleted` 등) | 순수 단위 | 가드 조건. |
| Repository | `@DataJpaTest` (H2) | `findByIdAndNotDeletedForUpdate` · `findAllOpenNotDeleted` 정렬·필터 · `findActiveLeader` · `findAllByStudyIdIn` batch fetch 등. |
| 스터디 전체 시나리오 5종 | `@SpringBootTest` + MockMvc + H2 | 컨트롤러부터 DB 까지 통합. SRS 검사기준 닫는 위치. |

**검증 종류 구분.**
- *동작 어서션*: HTTP status / body JSON / ErrorCode 문자열.
- *상태 어서션*: DB row 의 `status` / `is_deleted` / `current_member_count` / `max_members` / `study_tag` row 개수 / `study_language` row 개수.
- *상호작용 어서션*: Service 내부에서 `studyMemberRepository.save(... LEADER ...)` 호출 여부, `tagRepository.findAllByNameIn` → `saveAll` 순서.

세 종류를 매 케이스에 명시한다 (§4 표의 "검증 포인트" 칸).

---

## 3. 도구·픽스처·테스트 더블 결정

| 항목 | 결정 | 비고 |
|---|---|---|
| 통합/슬라이스 DB | **H2** (`MODE=MYSQL`) | 인증 도메인과 동일. `@Lock(PESSIMISTIC_WRITE)` 는 H2 에서 noop 에 가깝지만 D-009 동일 정책으로 동시성 회귀 테스트 제외 → 영향 없음. |
| 시간 | `Clock` 빈 주입. 테스트는 `Clock.fixed` 또는 `MutableClock`. | `createdAt DESC` 정렬 검증에 사용. |
| 인증 컨텍스트 | 인증 도메인의 `JwtTokenProvider` + `UserFixture` 로 실제 access token 발급해 헤더에 박는 통합 흐름. 슬라이스 테스트는 `@WithMockUser` 또는 `SecurityMockMvcRequestPostProcessors.user(...)`. | LEADER 권한 분기 검증 위해 user1/user2 구분 fixture 필요. |
| 픽스처 | `StudyFixture.openStudy(leaderId, maxMembers, currentMembers)` / `StudyFixture.closedStudy(...)` / `TagFixture.persist("Java")` / `LanguageFixture` 정적 메서드. JPA save 후 entity 반환. | 모든 통합 테스트가 공유. |
| 통합 테스트 트랜잭션 | `@Transactional` 사용 (롤백). 컨트롤러가 자체 트랜잭션 커밋한 상태를 어서션할 때는 `entityManager.clear()` 후 `findById` 또는 `JdbcTemplate` 으로 직접 조회. | 인증 도메인과 동일 패턴. |
| Jackson `Optional` 역직렬화 | `Jdk8Module` 등록 + 명시적 `null` 거부 deserializer. 테스트는 raw JSON 문자열로 `{}` / `{"title": null}` / `{"title": "x"}` 세 가지 케이스 모두 검증. | §4.4 PATCH 케이스의 핵심. |

---

## 4. 시나리오별 테스트 케이스 (study-class-design §3 1:1)

각 표의 "ID" 는 테스트 메서드 명에 그대로 박는다. 형식: `T-STUDY-<엔드포인트약자>-<번호>`.
"레이어": `U` = 단위, `WMVC` = `@WebMvcTest`, `DJ` = `@DataJpaTest`, `SBT` = `@SpringBootTest` 통합.

### 4.1 스터디 개설 (`POST /api/studies`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-STUDY-CREATE-01 | SBT | 사전: user A 로그인. 신규 태그 2개(`Java`, `Spring`) + 언어 2개(`ko`, `en`) 포함 정상 본문. | 201 `{studyId, title, status:OPEN, currentMembers:1, maxMembers}`. `study` row 1건 (`status=OPEN`, `is_deleted=0`, `current_member_count=1`, `duration_weeks` 본문값). `study_member` row 1건 (LEADER, user A, `is_active=1`). `study_tag` 2건. `study_language` 2건. `tag` 신규 2건 INSERT. | 동작/상태 |
| T-STUDY-CREATE-02 | SBT | 사전: `tag` 테이블에 `Java` 이미 존재. 본문 tags=[`Java`, `Spring`]. | 201. `tag` 테이블 INSERT 1건만 (`Spring`). `study_tag` 2건. | 상태/상호작용 |
| T-STUDY-CREATE-03 | SBT | 본문 tags=[`Java`, `Java`, `Spring`] (중복) | 201. 중복 제거 후 `study_tag` 2건. | 상태 |
| T-STUDY-CREATE-04 | SBT | 인증 헤더 없음. | 401 `UNAUTHORIZED`. `study` row 미생성. | 동작/상태 |
| T-STUDY-CREATE-05 | WMVC | `title` 1자 / 51자 | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-CREATE-06 | WMVC | `description` 2001자 | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-CREATE-07 | WMVC | `tags` = [] | 400 `INVALID_INPUT` (`@NotEmpty`) | 동작 |
| T-STUDY-CREATE-08 | WMVC | `tags` = 11개 | 400 `INVALID_INPUT` (`@Size(max=10)`) | 동작 |
| T-STUDY-CREATE-09 | WMVC | `tags` 원소 중 빈 문자열 / 31자 | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-CREATE-10 | WMVC | `languages` = [] / 11개 / 31자 원소 | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-CREATE-11 | WMVC | `maxMembers` = 1 / 51 / null | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-CREATE-12 | WMVC | `durationWeeks` = 0 / 53 / null | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-CREATE-13 | WMVC | `meetingCycle` = 빈 / 51자 | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-CREATE-14 | U (Service) | mock 리포지토리. `create` 호출 → `tagRepository.findAllByNameIn` → 누락 태그 `saveAll` → `studyRepository.save` → `studyMemberRepository.save(LEADER)` → `studyTagRepository.saveAll` → `studyLanguageRepository.saveAll` 순서 | Mockito `InOrder` (D-012: LEADER 동시 INSERT 순서 보장) | 상호작용 |

### 4.2 스터디 목록 조회 (`GET /api/studies?page&size`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-STUDY-LIST-01 | SBT | 사전: OPEN 3건(`createdAt` 다름) + CLOSED 1건 + soft-deleted 1건 (총 5건). 인증 user. | 200. `studies` 3건 (OPEN 만), `createdAt DESC` 정렬, `totalCount=3`, `page=0`, `size=20` | 동작/상태 |
| T-STUDY-LIST-02 | SBT | 사전: OPEN 25건. 요청 `page=0&size=10` | `studies.length=10`, `totalCount=25` | 동작 |
| T-STUDY-LIST-03 | SBT | 요청 `page=2&size=10` (위와 같은 사전) | `studies.length=5` (마지막 페이지), `page=2` | 동작 |
| T-STUDY-LIST-04 | SBT | OPEN 0건 | 200, `studies=[]`, `totalCount=0` | 동작 |
| T-STUDY-LIST-05 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-STUDY-LIST-06 | WMVC | `page=-1` | 400 `INVALID_INPUT` (`@Min(0)`) | 동작 |
| T-STUDY-LIST-07 | WMVC | `size=0` / `size=101` | 400 `INVALID_INPUT` | 동작 |
| T-STUDY-LIST-08 | SBT | 요청 파라미터 생략 | 디폴트 `page=0&size=20` 적용 | 동작 |
| T-STUDY-LIST-09 | SBT | 각 스터디에 tag 2~3개 부여 | summary 응답에 `tags` 포함, 각 스터디별 정확한 태그 묶음 (N+1 회피용 batch fetch 검증; 쿼리 카운트 어서션은 옵션) | 상태 |
| T-STUDY-LIST-10 | DJ | `StudyRepository.findAllOpenNotDeleted` | `is_deleted=1` / `status=CLOSED` row 제외 확인, `createdAt DESC` 정렬 확인 | 동작 |

### 4.3 스터디 상세 조회 (`GET /api/studies/{studyId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-STUDY-DETAIL-01 | SBT | 사전: OPEN study (tag 2, lang 2, LEADER user A, currentMembers=1). | 200. body 가 §8.3 `StudyDetailResponse` shape 와 일치 (`createdBy.userId/name` 채워짐, `tags`/`languages` 정확) | 동작/상태 |
| T-STUDY-DETAIL-02 | SBT | 미존재 studyId | 404 `NOT_FOUND` | 동작 |
| T-STUDY-DETAIL-03 | SBT | 사전: `is_deleted=1` study | 404 `NOT_FOUND` (soft delete 후 비노출) | 동작 |
| T-STUDY-DETAIL-04 | SBT | 사전: CLOSED study | 200 (목록에서만 빠지고 상세는 노출됨 — D-014 통합 모델: 팀 전용 공간 = 동일 row) | 동작 |
| T-STUDY-DETAIL-05 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |

### 4.4 스터디 수정 + 상태 토글 (`PATCH /api/studies/{studyId}`)

설계 §3.4 의 4분기 + Optional 시멘틱이 핵심.

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-STUDY-PATCH-01 | SBT | LEADER 가 `{"title":"new"}` PATCH | 200. `study.title` 갱신. 다른 필드 무변경. `updatedAt` 갱신. | 동작/상태 |
| T-STUDY-PATCH-02 | SBT | PATCH 본문 `{}` (빈 객체) | 200. DB 변경 없음 (no-op). | 동작/상태 |
| T-STUDY-PATCH-03 | SBT | 본문에 `{"title": null}` (명시적 null) | 400 `INVALID_INPUT` (deserializer 단계 거부) | 동작 |
| T-STUDY-PATCH-04 | SBT | 비-LEADER user 가 정상 본문 PATCH | 403 `FORBIDDEN` (메시지: "스터디 수정 권한이 없습니다.") | 동작/상태(무변경) |
| T-STUDY-PATCH-05 | SBT | 미존재 studyId | 404 `NOT_FOUND` | 동작 |
| T-STUDY-PATCH-06 | SBT | soft-deleted study | 404 `NOT_FOUND` | 동작 |
| T-STUDY-PATCH-07 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| **status 분기 (D-014)** | | | | |
| T-STUDY-PATCH-08 | SBT | OPEN study, `{"status":"CLOSED"}` | 200. `status=CLOSED`. | 동작/상태 |
| T-STUDY-PATCH-09 | SBT | CLOSED study (cur=2, max=5), `{"status":"OPEN"}` | 200. `status=OPEN`. | 동작/상태 |
| T-STUDY-PATCH-10 | SBT | CLOSED study (cur=5, max=5), `{"status":"OPEN"}` | 409 `INVALID_STATUS_TRANSITION` (정원 도달 재오픈 차단). status 무변경. | 동작/상태 |
| T-STUDY-PATCH-11 | SBT | OPEN study, `{"status":"OPEN"}` (동일 상태) | 200, 무변경 또는 idempotent (정책 픽싱: idempotent 200 로 둔다) | 동작 |
| T-STUDY-PATCH-12 | WMVC | `{"status":"CANCELLED"}` (enum 외) | 400 `INVALID_INPUT` | 동작 |
| **CLOSED 제한 분기 (D-015)** | | | | |
| T-STUDY-PATCH-13 | SBT | CLOSED study, `{"maxMembers": 10}` | 409 `INVALID_STATUS_FOR_UPDATE` | 동작/상태 |
| T-STUDY-PATCH-14 | SBT | CLOSED study, `{"durationWeeks": 8}` | 409 `INVALID_STATUS_FOR_UPDATE` | 동작 |
| T-STUDY-PATCH-15 | SBT | CLOSED study, `{"meetingCycle":"매주 화"}` | 409 `INVALID_STATUS_FOR_UPDATE` | 동작 |
| T-STUDY-PATCH-16 | SBT | CLOSED study, `{"title":"new", "description":"..."}` | 200 (title/description/tags/languages 는 마감 후에도 변경 가능) | 동작/상태 |
| T-STUDY-PATCH-17 | SBT | CLOSED study, `{"status":"OPEN", "maxMembers":10}` (재오픈 동시) | 동작: 같은 트랜잭션 분기 순서로 status 먼저 → OPEN 전이 성공 → maxMembers 변경 통과. 200. | 동작/상태 (분기 순서 검증) |
| **maxMembers 분기 (D-011)** | | | | |
| T-STUDY-PATCH-18 | SBT | OPEN study (cur=3), `{"maxMembers":2}` | 409 `INVALID_MAX_MEMBERS` | 동작/상태 |
| T-STUDY-PATCH-19 | SBT | OPEN study (cur=3), `{"maxMembers":3}` | 200 (cur 와 동일 허용) | 동작/상태 |
| T-STUDY-PATCH-20 | SBT | OPEN study, `{"maxMembers":10}` (증가) | 200 | 동작/상태 |
| T-STUDY-PATCH-21 | WMVC | `{"maxMembers":1}` / `{"maxMembers":51}` | 400 `INVALID_INPUT` | 동작 |
| **tags / languages 교체 분기** | | | | |
| T-STUDY-PATCH-22 | SBT | OPEN study (tags=[A,B]), `{"tags":["C","D","E"]}` | 200. 기존 `study_tag` 2건 삭제, 신규 3건 insert. (tag 테이블에 C/D/E 신규면 함께 INSERT) | 상태 |
| T-STUDY-PATCH-23 | SBT | `{"tags":[]}` | 400 `INVALID_INPUT` (`@NotEmpty` 가 Optional 내부에도 적용) | 동작 |
| T-STUDY-PATCH-24 | SBT | OPEN study, `{"languages":["ko"]}` | 200. `study_language` 전체 교체. | 상태 |
| **단위 분기 순서** | | | | |
| T-STUDY-PATCH-25 | U (Service) | LEADER 검증 → status 분기 → CLOSED 제한 분기 → maxMembers 분기 → 일반 필드 분기 호출 순서 확인 | Mockito `InOrder` | 상호작용 |
| T-STUDY-PATCH-26 | U (Service) | `update` 진입 시 `findByIdAndNotDeletedForUpdate` (락 메서드) 가 호출되는지 | Mockito verify | 상호작용 |

### 4.5 스터디 삭제 (`DELETE /api/studies/{studyId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-STUDY-DELETE-01 | SBT | LEADER 가 자신의 OPEN study DELETE | 204. `study.is_deleted=1`. `study_member` / `study_tag` / `study_language` row 보존. 이후 GET 상세 호출 시 404. | 동작/상태 |
| T-STUDY-DELETE-02 | SBT | LEADER 가 CLOSED study DELETE | 204. 동일. | 동작/상태 |
| T-STUDY-DELETE-03 | SBT | 비-LEADER user 가 DELETE | 403 `FORBIDDEN`. `is_deleted` 무변경. | 동작/상태 |
| T-STUDY-DELETE-04 | SBT | 미존재 studyId | 404 `NOT_FOUND` | 동작 |
| T-STUDY-DELETE-05 | SBT | 이미 soft-deleted study 재 DELETE | 404 `NOT_FOUND` (멱등 X — 이미 비노출 자원) | 동작/상태 |
| T-STUDY-DELETE-06 | SBT | 인증 헤더 없음 | 401 `UNAUTHORIZED` | 동작 |
| T-STUDY-DELETE-07 | SBT | DELETE 후 GET 목록 호출 | 해당 study 가 결과에서 제외됨 | 동작 |
| T-STUDY-DELETE-08 | U (Service) | `delete` 호출 시 `findByIdAndNotDeletedForUpdate` (락) → `assertLeader` → `study.markDeleted` 순서 | Mockito `InOrder` | 상호작용 |

---

## 5. 입력 유효성 테스트 매트릭스 (RE-NF-03)

`@WebMvcTest` + `MockMvc` 슬라이스에서 `StudyRequestValidationTest` 한 클래스로 묶는다.

| Request DTO | 필드 | 케이스 | 기대 |
|---|---|---|---|
| `StudyCreateRequest` | title | null / 빈 / 1자 / 51자 / 2자(경계) / 50자(경계) | null·빈·1·51 → 400; 2/50 통과 |
| `StudyCreateRequest` | description | null / 빈 / 2001자 / 2000자(경계) | null·빈·2001 → 400; 2000 통과 |
| `StudyCreateRequest` | tags | null / [] / [""] / ["a".repeat(31)] / 11개 / 정상 | null·빈·원소위반·11개 → 400 |
| `StudyCreateRequest` | languages | tags 와 동일 케이스 | 동일 |
| `StudyCreateRequest` | maxMembers | null / 1 / 2 / 50 / 51 | null·1·51 → 400; 2·50 통과 |
| `StudyCreateRequest` | durationWeeks | null / 0 / 1 / 52 / 53 | null·0·53 → 400; 1·52 통과 |
| `StudyCreateRequest` | meetingCycle | null / 빈 / 51자 / 50자(경계) | null·빈·51 → 400; 50 통과 |
| `StudyUpdateRequest` | (전체) | `{}` (빈 객체) | 통과 (Service no-op) |
| `StudyUpdateRequest` | 임의 필드 | `null` 명시 (`{"title": null}`) | 400 `INVALID_INPUT` (Jackson deserializer 거부) |
| `StudyUpdateRequest` | 값 명시 시 | `StudyCreateRequest` 와 동일 범위 검증 적용 | 동일 |
| `StudyUpdateRequest` | status | `"OPEN"` / `"CLOSED"` 외 (`"CANCELLED"`, 소문자 등) | 400 `INVALID_INPUT` |

응답 JSON shape: `{ "code": "INVALID_INPUT", "message": "<첫 위반 메시지>" }`. 인증 도메인 `ApiErrorResponse` 어서션 헬퍼 재사용.

---

## 6. SRS 검사기준 ↔ 테스트 매핑 표

| 검사기준 No | 요구사항 ID | 검사 요지 | 닫는 테스트 |
|---|---|---|---|
| 기능 No.X | RE-SF2-01 | 인증된 학생이 입력값 충족 시 스터디 개설 성공, 입력 누락 시 거절 | T-STUDY-CREATE-01 ~ T-STUDY-CREATE-13 (정상·검증) |
| 기능 No.X | RE-SF2-02 | 스터디장이 모집 마감 / 인원 축소 / 정보 수정 가능. 권한·정원·마감 후 제한 분기. | T-STUDY-PATCH-01·04·08·10·13~16·18~20 + T-STUDY-DELETE-01·03 |
| 기능 No.X | RE-SF2-02a | 스터디 삭제 시 이후 접근 차단 (조회·신청 흐름 비노출) | T-STUDY-DELETE-01·05·07 + T-STUDY-DETAIL-03 + T-STUDY-LIST-01 (soft-deleted 제외) |
| 기능 No.X | RE-SF2-03 | 모집 중인 스터디 목록을 최신순으로 조회, 페이징 동작 | T-STUDY-LIST-01·02·03·09·10 |
| 기능 No.X | RE-SF2-04 | 스터디 상세 조회로 카드에 노출되지 않은 정보까지 확인 | T-STUDY-DETAIL-01·04 |
| 비기능 No.3 | RE-NF-03 | 입력 형식 위반 시 오류 메시지 | §5 매트릭스 전부 |

비고: RE-NF-06 (동시성) 은 D-009 동일 정책으로 본 스프린트 회귀 테스트 스코프 외. 코드 레벨 방어(`PESSIMISTIC_WRITE`, 단일 트랜잭션) 는 유지. 검사기준 번호는 SRS v2.4 의 검사기준 표에 맞춰 채워넣을 것 (추적표 갱신 시점에 일괄 매핑).

---

## 7. 커버 / 스킵 정책

**커버**
- 비즈니스 핵심 경로: create → list → detail → patch(상태/필드/maxMembers) → delete 정상 흐름과 실패 분기.
- 에러 핸들링: 본 도메인 5 ErrorCode (`NOT_FOUND`, `FORBIDDEN`, `INVALID_MAX_MEMBERS`, `INVALID_STATUS_TRANSITION`, `INVALID_STATUS_FOR_UPDATE`) + 공통 `INVALID_INPUT` / `UNAUTHORIZED` 각각 최소 1개 케이스.
- 엣지 케이스: maxMembers == currentMemberCount 경계, 정원 도달 재오픈 차단 경계, `{}` no-op, `{"x": null}` 거부, soft-deleted 비노출, OPEN→CLOSED→OPEN 왕복.
- 보안 경계: 비-LEADER PATCH/DELETE 차단, 인증 없는 모든 엔드포인트 401.
- 데이터 무결성: tag/language 전체 교체 시 기존 row 삭제, soft delete 후 자식 row 보존.

**스킵**
- DTO record 의 자동 생성 메서드.
- Spring Security 프레임워크 내부.
- Pageable 내부 동작 (Spring Data 위임).
- `GlobalExceptionHandler` 매핑 자체 (인증 도메인에서 이미 깎음, 본 스프린트는 status + code 만 어서션).
- 부하 / 응답 시간 (RE-NF-04 본 스프린트 외).
- **동시성 회귀 통합 테스트** (D-009 동일 정책).
- Swagger 문서 생성/노출 검증 (별도 단계).

---

## 8. 커버리지 목표

- `study.service` 패키지 line coverage **90% 이상**, branch coverage **85% 이상** (PATCH 4분기 모두 닫혀야 함).
- `study.controller` 패키지: 메서드별 정상/실패 1개씩 닫혔으면 충분.
- `study.domain` (Entity 도메인 메서드): branch coverage **100%** (`changeMaxMembers` / `changeStatus` 의 가드 조건 포함 — 가드는 Service 가 우선 막지만 Entity 도 방어).
- 미달 시 강제 머지 차단은 없음. 1차 게이트는 본 문서 §4 ID 가 코드로 1:1 옮겨졌는지.

---

## 9. 다음 단계 진입 체크리스트

테스트 코드 작성 직전 확인:

- [ ] §10.3 DB 마이그레이션 적용 (`studymate_schema.sql` 갱신 + `application-test.yml` 의 H2 스키마 자동 생성에 반영)
- [ ] `com.studymate.common.exception.ErrorCode` 에 신규 3건 추가 (INVALID_MAX_MEMBERS / INVALID_STATUS_TRANSITION / INVALID_STATUS_FOR_UPDATE) — 도메인 코드 (`STUDY_NOT_FOUND` / `FORBIDDEN_STUDY`) 의 직렬화 어휘는 시트 그대로 `NOT_FOUND` / `FORBIDDEN` 출력하도록 직렬화 처리
- [ ] Jackson `Optional` 역직렬화 설정 (`Jdk8Module` + null 거부 deserializer)
- [ ] `StudyFixture` / `TagFixture` / `LanguageFixture` 정적 헬퍼 작성
- [ ] §4 의 모든 ID 가 테스트 메서드명에 1:1 매핑되도록 패키지 구조 결정 (예: `study/StudyCreateIntegrationTest.java`, `study/StudyPatchIntegrationTest.java`, `study/StudyRequestValidationTest.java`)
- [ ] LEADER / 비-LEADER user 2명 발급 헬퍼 (`AuthFixture.loginAs(...)` 재사용)

이 6개를 닫으면 본 문서 작성 시점부터 코드 한 줄도 새로 결정할 일 없이 깎을 수 있다. 결정이 필요한 항목이 나타나면 `docs/decisions-log.md` 에 등록 후 본 문서를 업데이트한다.
