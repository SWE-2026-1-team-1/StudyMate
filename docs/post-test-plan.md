# Sprint 4 도메인 C — 팀 게시판 테스트 플랜

> **목적.** 다음 단계(테스트 코드 작성)의 입력. `docs/post-class-design.md` 의 외부 시그니처 + SRS RE-SF4-02/03 검사기준 + D-020 결정을 테스트 케이스 ID 단위로 깎아둔다.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 코드 작성).
>
> **Scope 외 명시.** 부하 · E2E · UI · 보안 침투 · **동시성 회귀 테스트(D-009 / D-020 §20.c)** · 알림(P-009).

---

## 목차

1. 입력 체크리스트
2. 테스트 피라미드 매핑 + 컴포넌트별 전략
3. 도구·픽스처·테스트 더블 결정
4. 시나리오별 테스트 케이스 (post-class-design §3 1:1)
   - 4.1 게시글 목록
   - 4.2 게시글 생성
   - 4.3 게시글 상세
   - 4.4 게시글 수정
   - 4.5 게시글 삭제
   - 4.6 댓글 목록
   - 4.7 댓글 생성
   - 4.8 댓글 수정
   - 4.9 댓글 삭제
5. 입력 유효성 테스트 매트릭스 (RE-NF-03)
6. SRS 검사기준 ↔ 테스트 매핑 표
7. 커버 / 스킵 정책
8. 커버리지 목표
9. 다음 단계 진입 체크리스트

---

## 1. 입력 체크리스트

- [x] `docs/post-class-design.md` §2 ~ §10
- [x] `docs/decisions-log.md` D-020 (§20.a / §20.d / §20.g / §20.h / §20.l)
- [x] SRS RE-SF4-02 / RE-SF4-03 검사기준
- [x] `studymate_schema.sql` 의 `post` / `post_comment` 컬럼 (마이그레이션 없음)
- [x] D-020 §20.k — 본 도메인 신규 ErrorCode 0건 (재사용 매트릭스 §9 (class-design))
- [x] Sprint 3 / 도메인 A·B 인프라 (StudyFixture, StudyMemberFixture, UserFixture, JWT) 재사용
- [x] `study_role` 시드 (`LEADER` `can_post_notice=1` `can_manage_member=1`, `MEMBER` `can_post_notice=0`)

---

## 2. 테스트 피라미드 매핑 + 컴포넌트별 전략

| 컴포넌트 | 테스트 타입 | 이유 |
|---|---|---|
| `PostController` / `PostCommentController` | `@WebMvcTest` 슬라이스 + `MockMvc`, Service mock | 라우팅 / HTTP status / JSON 직렬화 / `@AuthenticationPrincipal` / `@Valid` 입력 유효성. |
| `PostService` / `PostCommentService` | 순수 Mockito 단위 | 권한 분기 (작성자 / LEADER / NOTICE 권한) / soft delete 분기 / 호출 순서. |
| Entity 도메인 메서드 (`Post.create` / `update` / `softDelete`, `PostComment.create` / `update` / `softDelete`) | 순수 단위 | 필드 세팅 / `updated_at` 갱신 / 이미 soft delete 된 row 재호출 예외. |
| Repository | `@DataJpaTest` (H2) | `findAllByStudyIdAndNotDeleted` 정렬·페이징, `findByIdAndStudyIdAndNotDeletedForUpdate`, `findAllByPostIdAndNotDeletedOrderByCreatedAtAsc`, `findActiveNoticeWriter` JPQL join. |
| 전체 시나리오 9종 | `@SpringBootTest` + MockMvc + H2 | 컨트롤러 → DB. SRS 검사기준 닫는 위치. |

**검증 종류.**
- *동작*: HTTP status / body / ErrorCode 문자열 / 메시지 오버라이드 (NOTICE 권한 / 작성자 권한 / 댓글 권한).
- *상태*: `post.is_deleted` / `deleted_at` / `updated_at`, `post_comment.is_deleted` / `deleted_at` / `updated_at`, 생성된 row 식별자.
- *상호작용*: Service 내부 호출 순서 — `studyRepository.findByIdAndNotDeleted` → 멤버 검증 → post 조회/락 → 권한 분기 → 도메인 메서드.

---

## 3. 도구·픽스처·테스트 더블 결정

| 항목 | 결정 | 비고 |
|---|---|---|
| 통합/슬라이스 DB | **H2** (`MODE=MYSQL`) | Sprint 2·3·도메인 A·B 동일. |
| 시간 | `Clock` 빈 주입. `created_at` / `updated_at` 어서션은 `Clock.fixed`. | |
| 인증 컨텍스트 | LEADER user A (`can_post_notice=1`) / MEMBER user B (`can_post_notice=0`) / MEMBER user D / 비-멤버 user C. | NOTICE 권한 + 작성자 권한 + LEADER 삭제 권한 시나리오. |
| 픽스처 | `PostFixture.notice(studyId, authorMemberId)` / `PostFixture.free(studyId, authorMemberId)` (신규). `PostCommentFixture.of(studyId, postId, authorMemberId)` (신규). `StudyMemberFixture.leader/member` 재사용. | |
| 통합 테스트 트랜잭션 | `@Transactional` 사용. 커밋 후 상태 어서션 시 `entityManager.clear()` 후 `findById`. | Sprint 3 동일. |
| `study_role` 시드 | LEADER (`can_post_notice=1` `can_manage_member=1`), MEMBER (`can_post_notice=0` `can_manage_member=0`) 필수. | NOTICE 권한 + 게시글 LEADER 삭제 권한 동작 필수. |

---

## 4. 시나리오별 테스트 케이스

ID 형식: `T-POST-<엔드포인트약자>-<번호>` / `T-CMT-<엔드포인트약자>-<번호>`.
레이어 약자: `U` = 단위, `WMVC` = `@WebMvcTest`, `DJ` = `@DataJpaTest`, `SBT` = `@SpringBootTest` 통합.

### 4.1 게시글 목록 (`GET /api/teams/{teamId}/posts`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-POST-LIST-01 | SBT | study (A=LEADER, B=MEMBER). 게시글 3건 (createdAt T0/T1/T2). user A `?page=0&size=20`. | 200. `posts.length=3`, `createdAt DESC` (T2→T0). `totalCount=3`. 각 row 에 postId/title/type/authorName/createdAt. | 동작/상태 |
| T-POST-LIST-02 | SBT | 활성 2 + soft-deleted 1. user A. | length=2 (deleted 제외). | 동작 |
| T-POST-LIST-03 | SBT | NOTICE 1 + FREE 2 mix. user A. | length=3, type 필드 정확. (분리 안 함.) | 동작 |
| T-POST-LIST-04 | SBT | 비-멤버 user C. | 403 `FORBIDDEN` ("팀 접근 권한이 없습니다."). | 동작 |
| T-POST-LIST-05 | SBT | 탈퇴(`is_active=0`) user B. | 403 `FORBIDDEN`. | 동작 |
| T-POST-LIST-06 | SBT | MEMBER user B 활성. | 200. (LEADER 권한 불필요.) | 동작 |
| T-POST-LIST-07 | SBT | 미존재 teamId. | 404 `NOT_FOUND`. | 동작 |
| T-POST-LIST-08 | SBT | soft-deleted study. | 404 `NOT_FOUND`. | 동작 |
| T-POST-LIST-09 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-POST-LIST-10 | SBT | 게시글 25건. `size=20`. | length=20, `totalCount=25`. | 페이징 |
| T-POST-LIST-11 | SBT | 게시글 25건. `page=1&size=20`. | length=5. | 페이징 |
| T-POST-LIST-12 | SBT | `size=101` (max 초과). | 400 `INVALID_INPUT`. | 동작 (D-015) |
| T-POST-LIST-13 | SBT | `page=-1`. | 400. | 동작 |
| T-POST-LIST-14 | DJ  | `findAllByStudyIdAndNotDeleted` | 정렬 DESC / deleted 제외 검증. | 동작 |

### 4.2 게시글 생성 (`POST /api/teams/{teamId}/posts`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-POST-CREATE-01 | SBT | user A (LEADER). `{title, content, type:"FREE"}`. | 201. body `{postId, title, createdAt}`. DB row 생성 (`is_deleted=0`, type=FREE, author_member_id=LEADER.id). | 동작/상태 |
| T-POST-CREATE-02 | SBT | user A. `{type:"NOTICE"}`. | 201. type=NOTICE row 생성. | 동작 (D-020 §20.d) |
| T-POST-CREATE-03 | SBT | MEMBER user B. `{type:"NOTICE"}`. | 403 `FORBIDDEN` ("공지 작성 권한이 없습니다."). row 미생성. | 동작/상태 (D-020 §20.d) |
| T-POST-CREATE-04 | SBT | MEMBER user B. `{type:"FREE"}`. | 201. | 동작 |
| T-POST-CREATE-05 | SBT | 비-멤버 user C. | 403 `FORBIDDEN` ("팀 접근 권한이 없습니다."). | 동작 |
| T-POST-CREATE-06 | SBT | 미존재 teamId. | 404. | 동작 |
| T-POST-CREATE-07 | SBT | soft-deleted study. | 404. | 동작 |
| T-POST-CREATE-08 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-POST-CREATE-09 | WMVC | body `{title:"", content:"x", type:"FREE"}`. | 400 `INVALID_INPUT`. | 입력 유효성 |
| T-POST-CREATE-10 | WMVC | `title` 301자. | 400. | 유효성 |
| T-POST-CREATE-11 | WMVC | `content:""`. | 400. | 유효성 |
| T-POST-CREATE-12 | WMVC | `content` 5001자. | 400. | 유효성 |
| T-POST-CREATE-13 | WMVC | `type` 누락. | 400. | 유효성 |
| T-POST-CREATE-14 | WMVC | `type:"UNKNOWN"`. | 400 (enum 역직렬화 실패 → `HttpMessageNotReadableException` 핸들러). | 유효성 |

### 4.3 게시글 상세 (`GET /api/teams/{teamId}/posts/{postId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-POST-DETAIL-01 | SBT | FREE post, user A. | 200. body 정확. `updatedAt == createdAt`. | 동작 |
| T-POST-DETAIL-02 | SBT | NOTICE post, MEMBER user B 조회. | 200. (NOTICE 조회는 활성 멤버 모두 허용.) | 동작 |
| T-POST-DETAIL-03 | SBT | soft-deleted post. | 404 `NOT_FOUND`. | 동작 |
| T-POST-DETAIL-04 | SBT | 다른 study 의 postId. | 404 (`studyId` 미일치). | 동작 |
| T-POST-DETAIL-05 | SBT | 비-멤버 user C. | 403. | 동작 |
| T-POST-DETAIL-06 | SBT | 미존재 postId. | 404. | 동작 |
| T-POST-DETAIL-07 | SBT | 미존재 teamId. | 404. | 동작 |
| T-POST-DETAIL-08 | SBT | 인증 헤더 없음. | 401. | 동작 |

### 4.4 게시글 수정 (`PATCH /api/teams/{teamId}/posts/{postId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-POST-PATCH-01 | SBT | FREE post (author=A). user A `{title:"new"}`. | 200. body `{postId, title:"new", updatedAt}`. DB title 변경 / content 유지 / updated_at 갱신. | 동작/상태 |
| T-POST-PATCH-02 | SBT | A 가 `{content:"new"}` 만 전송. | 200. content 변경, title 유지. | 동작 (PATCH 시멘틱) |
| T-POST-PATCH-03 | SBT | A 가 `{}` 전송. | 200. 모든 필드 유지. updated_at 만 갱신 — 또는 결정에 따라 미갱신. **본 결정**: 도메인 메서드 호출 → updated_at 갱신. | 동작 |
| T-POST-PATCH-04 | SBT | A 가 `{title:null}` 전송. | 400 `INVALID_INPUT`. (D-015 패턴 — null 명시는 거절.) | 동작 (D-015) |
| T-POST-PATCH-05 | SBT | post.author=A, LEADER 가 다른 사람의 게시글에 수정 시도 (caller=LEADER, author=MEMBER). | 403 `FORBIDDEN` ("게시글 수정 권한이 없습니다."). | 동작 (D-020 §20.h) |
| T-POST-PATCH-06 | SBT | post.author=B, MEMBER user D 가 수정 시도. | 403. | 동작 |
| T-POST-PATCH-07 | SBT | 비-멤버 user C. | 403. | 동작 |
| T-POST-PATCH-08 | SBT | soft-deleted post. | 404. | 동작 |
| T-POST-PATCH-09 | SBT | 미존재 postId / teamId / study deleted. | 404. | 동작 |
| T-POST-PATCH-10 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-POST-PATCH-11 | WMVC | `title:"x".repeat(301)`. | 400. | 유효성 |
| T-POST-PATCH-12 | WMVC | `content` 5001자. | 400. | 유효성 |

### 4.5 게시글 삭제 (`DELETE /api/teams/{teamId}/posts/{postId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-POST-DELETE-01 | SBT | post.author=A, user A 삭제. | 204. `post.is_deleted=1`, `deleted_at != null`, `updated_at == deleted_at`. row 보존. | 동작/상태 |
| T-POST-DELETE-02 | SBT | post.author=B (MEMBER), LEADER user A 삭제. | 204. (LEADER 면제 — D-020 §20.h) | 동작 (D-020 §20.h) |
| T-POST-DELETE-03 | SBT | post.author=B, MEMBER user D 삭제. | 403 `FORBIDDEN` ("게시글 삭제 권한이 없습니다."). | 동작 |
| T-POST-DELETE-04 | SBT | 비-멤버 user C. | 403 ("팀 접근 권한이 없습니다."). | 동작 |
| T-POST-DELETE-05 | SBT | post 에 댓글 2건 매달림. user A 삭제. | 204. `post.is_deleted=1`. 댓글 row 는 그대로 (cascade 없음 — §11.4). | 동작/상태 |
| T-POST-DELETE-06 | SBT | 이미 soft-deleted post. | 404. | 동작 |
| T-POST-DELETE-07 | SBT | 미존재 postId / teamId / study deleted. | 404. | 동작 |
| T-POST-DELETE-08 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-POST-DELETE-09 | U (Entity) | `Post.softDelete()` — 이미 deleted. | `IllegalStateException`. | 동작 |
| T-POST-DELETE-10 | U (Service) | mock. delete 호출 순서: `findByIdAndNotDeleted(study)` → `findActiveByStudyIdAndUserId` → `findByIdAndStudyIdAndNotDeletedForUpdate(post)` → author 분기 / LEADER 분기 (`findActiveManager`) → `post.softDelete()` | Mockito `InOrder`. | 상호작용 |

### 4.6 댓글 목록 (`GET .../posts/{postId}/comments`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-CMT-LIST-01 | SBT | post 에 댓글 3건 (createdAt T0/T1/T2). user A. | 200. length=3, `createdAt ASC` (T0→T2). totalCount=3. | 동작 |
| T-CMT-LIST-02 | SBT | 활성 2 + soft-deleted 1. | length=2. | 동작 |
| T-CMT-LIST-03 | SBT | post 자체 soft-deleted. | 404. | 동작 |
| T-CMT-LIST-04 | SBT | 비-멤버. | 403. | 동작 |
| T-CMT-LIST-05 | SBT | 미존재 postId. | 404. | 동작 |
| T-CMT-LIST-06 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-CMT-LIST-07 | DJ  | `findAllByPostIdAndNotDeletedOrderByCreatedAtAsc` | 정렬 ASC / deleted 제외. | 동작 |

### 4.7 댓글 생성 (`POST .../posts/{postId}/comments`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-CMT-CREATE-01 | SBT | user B (MEMBER) `{content:"hi"}`. | 201. body `{commentId, content, authorName, createdAt}`. DB row 생성. | 동작/상태 |
| T-CMT-CREATE-02 | SBT | user A (LEADER) — NOTICE post 에 댓글. | 201. | 동작 |
| T-CMT-CREATE-03 | SBT | 비-멤버 user C. | 403. | 동작 |
| T-CMT-CREATE-04 | SBT | post soft-deleted. | 404. | 동작 |
| T-CMT-CREATE-05 | SBT | 미존재 postId / teamId / study deleted. | 404. | 동작 |
| T-CMT-CREATE-06 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-CMT-CREATE-07 | WMVC | `content:""`. | 400. | 유효성 |
| T-CMT-CREATE-08 | WMVC | `content` 1001자. | 400. | 유효성 |

### 4.8 댓글 수정 (`PATCH .../comments/{commentId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-CMT-PATCH-01 | SBT | comment.author=B, user B `{content:"new"}`. | 200. body `{commentId, content, updatedAt}`. DB content 변경. | 동작/상태 |
| T-CMT-PATCH-02 | SBT | comment.author=B, LEADER user A 수정 시도. | 403 `FORBIDDEN` ("댓글 수정 권한이 없습니다."). | 동작 (D-020 §20.h) |
| T-CMT-PATCH-03 | SBT | comment.author=B, MEMBER user D 수정. | 403. | 동작 |
| T-CMT-PATCH-04 | SBT | comment soft-deleted. | 404. | 동작 |
| T-CMT-PATCH-05 | SBT | post soft-deleted. | 404. (comment 조회 전 post 검증 단계에서 차단.) | 동작 |
| T-CMT-PATCH-06 | SBT | path 의 postId 와 comment.postId 불일치. | 404. | 동작 |
| T-CMT-PATCH-07 | SBT | 비-멤버 user C. | 403. | 동작 |
| T-CMT-PATCH-08 | SBT | 미존재 commentId / postId / teamId / study deleted. | 404. | 동작 |
| T-CMT-PATCH-09 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-CMT-PATCH-10 | WMVC | `content:""`. | 400. | 유효성 |
| T-CMT-PATCH-11 | WMVC | `content` 1001자. | 400. | 유효성 |

### 4.9 댓글 삭제 (`DELETE .../comments/{commentId}`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-CMT-DELETE-01 | SBT | comment.author=B, user B. | 204. `comment.is_deleted=1`, `deleted_at != null`. row 보존. | 동작/상태 |
| T-CMT-DELETE-02 | SBT | comment.author=B, LEADER user A 삭제 시도. | 403 ("댓글 삭제 권한이 없습니다."). | 동작 (D-020 §20.h) |
| T-CMT-DELETE-03 | SBT | comment.author=B, MEMBER user D. | 403. | 동작 |
| T-CMT-DELETE-04 | SBT | 비-멤버. | 403. | 동작 |
| T-CMT-DELETE-05 | SBT | 이미 soft-deleted comment. | 404. | 동작 |
| T-CMT-DELETE-06 | SBT | post soft-deleted. | 404. | 동작 |
| T-CMT-DELETE-07 | SBT | 미존재 commentId. | 404. | 동작 |
| T-CMT-DELETE-08 | SBT | 인증 헤더 없음. | 401. | 동작 |
| T-CMT-DELETE-09 | U (Entity) | `PostComment.softDelete()` — 이미 deleted. | `IllegalStateException`. | 동작 |

---

## 5. 입력 유효성 테스트 매트릭스 (RE-NF-03)

`@WebMvcTest` + `MockMvc` 슬라이스 `PostRequestValidationTest` / `CommentRequestValidationTest` 두 클래스.

| 엔드포인트 | 케이스 | 기대 |
|---|---|---|
| POST /posts | `title=""` | 400 `INVALID_INPUT` |
| POST /posts | `title.length=301` | 400 |
| POST /posts | `content=""` | 400 |
| POST /posts | `content.length=5001` | 400 |
| POST /posts | `type=null` | 400 |
| POST /posts | `type="UNKNOWN"` | 400 (enum 역직렬화) |
| PATCH /posts/{id} | `title.length=301` | 400 |
| PATCH /posts/{id} | `content.length=5001` | 400 |
| PATCH /posts/{id} | `title=null` body 키 명시 | 400 (D-015) |
| GET /posts ? `page=-1` | 400 | (Spring MVC + `@Min`) |
| GET /posts ? `size=101` | 400 | (Spring MVC + `@Max`) |
| POST /comments | `content=""` | 400 |
| POST /comments | `content.length=1001` | 400 |
| PATCH /comments/{id} | `content=""` | 400 |
| PATCH /comments/{id} | `content.length=1001` | 400 |
| (모든) PathVariable 비숫자 | 400 (Spring MVC 자동) | |

---

## 6. SRS 검사기준 ↔ 테스트 매핑 표

| 검사기준 No | 요구사항 ID | 검사 요지 | 닫는 테스트 |
|---|---|---|---|
| 기능 No.X | RE-SF4-02 | 활성 멤버가 팀 게시판에 글 작성, NOTICE 권한 분기 | T-POST-CREATE-01 · 02 · 03 · 04 · 05 |
| 기능 No.X | RE-SF4-03 | 활성 멤버가 댓글 작성/조회/수정/삭제, 작성자 본인만 수정·삭제 | T-CMT-CREATE-01 · 03, T-CMT-LIST-01, T-CMT-PATCH-01 · 02, T-CMT-DELETE-01 · 02 |
| 기능 No.X | (시트) 게시글 CRUD | 작성자 권한 수정 / 작성자+LEADER 삭제 / 소프트 삭제 | T-POST-LIST-01 · 02, T-POST-DETAIL-01, T-POST-PATCH-01 · 05, T-POST-DELETE-01 · 02 · 03 |
| 비기능 No.2 | RE-NF-02 | 팀 전용 공간 접근 제어 (비멤버/탈퇴 차단) | T-POST-LIST-04 · 05, T-CMT-CREATE-03, T-POST-DETAIL-05 |
| 비기능 No.3 | RE-NF-03 | 입력 형식 위반 시 오류 메시지 | §5 매트릭스 |

비고:
- **RE-NF-06 (동시성)** — D-009 / D-020 §20.c 정책으로 회귀 테스트 스코프 외. 본 도메인은 study row 락 미사용 (게시판이 정원/마감과 무관). post / comment row 락만 단위 verify (T-POST-DELETE-10 등) 에서 호출 순서 확인.

---

## 7. 커버 / 스킵 정책

**커버**
- 정상 흐름: list / create / detail / update / delete (게시글 5 + 댓글 4).
- 권한 분기:
  - 팀 접근 (활성 멤버) 가드.
  - NOTICE 작성 권한 (`can_post_notice`).
  - 게시글 수정 = 작성자, 게시글 삭제 = 작성자 + LEADER.
  - 댓글 수정/삭제 = 작성자.
- soft delete 정합: `is_deleted=1`, `deleted_at`, row 보존. 부모 게시글 삭제 시 댓글 row 보존.
- 페이징: `page`/`size` 경계 (0 / max 100).
- 정렬: 게시글 DESC / 댓글 ASC.
- 보안 경계: 401 / 403 / 404 매트릭스.

**스킵**
- DTO record 자동 메서드.
- Spring Security 프레임워크 내부.
- `GlobalExceptionHandler` 매핑 자체.
- FULLTEXT 검색 (`ft_post_title_content`) — 본 스프린트 미사용.
- 부하 / 응답 시간 (RE-NF-04).
- **동시성 회귀 통합 테스트** (D-009 / D-020 §20.c).
- 알림 (P-009).

---

## 8. 커버리지 목표

- `post.service.PostService` / `post.service.PostCommentService` line **90% 이상**, branch **85% 이상**.
- `post.controller.PostController` / `post.controller.PostCommentController` 메서드별 정상/실패 1개씩.
- `Post.update` / `Post.softDelete` / `PostComment.update` / `PostComment.softDelete`: branch **100%**.

---

## 9. 다음 단계 진입 체크리스트

테스트 코드 작성 직전 확인:

- [ ] `Post` / `PostType` / `PostComment` 엔티티 신설 (`com.studymate.post.domain`).
- [ ] `PostRepository` / `PostCommentRepository` 신설 (§6 쿼리 3+2건).
- [ ] `StudyMemberRepository.findActiveNoticeWriter` 추가.
- [ ] `StudyRole.canPostNotice` 매핑 확인 (없으면 보강).
- [ ] Request DTO 4종 + Response DTO 7종 추가 (§8).
- [ ] `PostService` / `PostCommentService` 신규 (§5).
- [ ] `PostController` / `PostCommentController` 신규 (§4).
- [ ] 통합 테스트 클래스 분리: `PostListIntegrationTest` / `PostCreateIntegrationTest` / `PostDetailIntegrationTest` / `PostUpdateIntegrationTest` / `PostDeleteIntegrationTest` / `CommentListIntegrationTest` / `CommentCreateIntegrationTest` / `CommentUpdateIntegrationTest` / `CommentDeleteIntegrationTest`. 단위 `PostServiceTest` / `PostCommentServiceTest` / `PostDomainTest` / `PostCommentDomainTest`. 슬라이스 `PostRequestValidationTest` / `CommentRequestValidationTest`.
- [ ] LEADER user A / MEMBER user B / MEMBER user D / 비-멤버 user C fixture 4종 발급 헬퍼.
- [ ] `PostFixture.notice/free` / `PostCommentFixture.of` 신규.

이 체크리스트 닫으면 본 문서로 코드 한 줄도 새 결정 없이 깎을 수 있다.
