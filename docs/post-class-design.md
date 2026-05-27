# Sprint 4 도메인 C — 팀 게시판 클래스 설계

> **Scope.** Sprint 4 D-020 §20.a 의 도메인 C 9개 엔드포인트(게시글 CRUD 5 + 댓글 CRUD 4) 구현을 위한 클래스 토폴로지 / 메서드 시그니처 / DTO·Entity 필드 / 에러 코드 / 동시성 정책.
>
> **목적.** 본 문서 결과물이 다음 단계(테스트 작성)의 입력. 외부 시그니처와 계약만 픽싱.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 작성). 2차: 최평화(클라이언트 흐름 확인).
>
> **전제 결정.** `docs/decisions-log.md` D-003 / D-010 ~ D-020, `docs/claude-context.md` §1~§3, `docs/application-class-design.md` (도메인 A), `docs/member-class-design.md` (도메인 B).
>
> **본 문서 대상 엔드포인트 (9건).**
>
> 게시글:
> - `GET    /api/teams/{teamId}/posts`                                      — 게시글 목록
> - `POST   /api/teams/{teamId}/posts`                                      — 게시글 생성
> - `GET    /api/teams/{teamId}/posts/{postId}`                             — 게시글 상세
> - `PATCH  /api/teams/{teamId}/posts/{postId}`                             — 게시글 수정
> - `DELETE /api/teams/{teamId}/posts/{postId}`                             — 게시글 삭제
>
> 댓글:
> - `GET    /api/teams/{teamId}/posts/{postId}/comments`                    — 댓글 목록
> - `POST   /api/teams/{teamId}/posts/{postId}/comments`                    — 댓글 생성
> - `PATCH  /api/teams/{teamId}/posts/{postId}/comments/{commentId}`        — 댓글 수정
> - `DELETE /api/teams/{teamId}/posts/{postId}/comments/{commentId}`        — 댓글 삭제
>
> **`teamId` 의미.** D-010 에 따라 `teamId == studyId`. 컨트롤러 진입 직후 동일 변수로 취급.

---

## 1. 패키지 토폴로지

도메인 A(`application`) 와 동등하게 **신규 `com.studymate.post` 패키지** 신설. 사유:
- 엔티티 두 종(`Post` / `PostComment`) 가 본 도메인의 고유 자원이고, `study` 패키지에 흡수하면 `study` 패키지가 과적재됨.
- 도메인 B(`StudyMember`) 와 달리 `Post` / `PostComment` 는 `Study` 라이프사이클의 부분집합이 아니라 자체 라이프사이클을 가짐 (CRUD 독립).

```
com.studymate.post
├── controller
│   ├── PostController                  // /api/teams/{teamId}/posts/*           (신규)
│   └── PostCommentController           // /api/teams/{teamId}/posts/{postId}/comments/* (신규)
├── service
│   ├── PostService                     // 5 메서드                              (신규)
│   └── PostCommentService              // 4 메서드                              (신규)
├── domain
│   ├── Post                            // 게시글 엔티티                          (신규)
│   ├── PostType                        // enum {NOTICE, FREE}                   (신규)
│   └── PostComment                     // 댓글 엔티티                            (신규)
├── repository
│   ├── PostRepository                                                            (신규)
│   └── PostCommentRepository                                                     (신규)
└── dto
    ├── request
    │   ├── PostCreateRequest                                                     (신규)
    │   ├── PostUpdateRequest                                                     (신규)
    │   ├── CommentCreateRequest                                                  (신규)
    │   └── CommentUpdateRequest                                                  (신규)
    └── response
        ├── PostSummaryResponse                                                   (신규)
        ├── PostListResponse                                                      (신규)
        ├── PostDetailResponse                                                    (신규)
        ├── PostCreateResponse                                                    (신규)
        ├── PostUpdateResponse                                                    (신규)
        ├── CommentResponse                                                       (신규)
        └── CommentListResponse                                                   (신규)
```

공통 패키지: `com.studymate.common` (ErrorCode / GlobalExceptionHandler 재사용).
의존 패키지: `com.studymate.study` (`StudyRepository`, `StudyMemberRepository`, `StudyMember`), `com.studymate.application` (`StudyRole` — `can_post_notice` 읽기), `com.studymate.auth` (`UserRepository` — 작성자 이름 응답용).

---

## 2. 클래스별 책임 + 의존성

### 2.1 PostController
- 책임: 게시글 5 엔드포인트 HTTP 어댑터. `@RequestMapping("/api/teams/{teamId}/posts")`.
- 메서드: `list`, `create`, `detail`, `update`, `delete`.
- 의존: `PostService`.
- 인증: 전 메서드 필수. 또한 `assertActiveMember` 로 팀 접근 가드.

### 2.2 PostCommentController
- 책임: 댓글 4 엔드포인트 HTTP 어댑터. `@RequestMapping("/api/teams/{teamId}/posts/{postId}/comments")`.
- 메서드: `list`, `create`, `update`, `delete`.
- 의존: `PostCommentService`.
- 인증: 전 메서드 필수.

### 2.3 PostService
- 책임: 게시글 5 엔드포인트 도메인 트랜잭션. D-020 §20.d (NOTICE 권한), §20.g (soft delete), §20.h (수정/삭제 권한) 단일 진입점.
- 의존: `PostRepository`, `StudyRepository`, `StudyMemberRepository`, `UserRepository`.
- 트랜잭션 경계: 메서드 1건 = 트랜잭션 1건. 조회는 `readOnly=true`.

### 2.4 PostCommentService
- 책임: 댓글 4 엔드포인트 도메인 트랜잭션. D-020 §20.h (작성자 본인만 수정/삭제) 단일 진입점.
- 의존: `PostCommentRepository`, `PostRepository`, `StudyRepository`, `StudyMemberRepository`, `UserRepository`.

### 2.5 Repository 계층
- Spring Data JPA. 본 도메인 커스텀 쿼리는 §6.
- `StudyRepository.findByIdAndNotDeleted` (조회) / `findByIdAndNotDeletedForUpdate` (잠금) 재사용. 본 도메인은 study row 락 **사용하지 않음** — 게시판은 정원/멤버 카운트 변경과 무관 (§10.1).

---

## 3. 데이터 플로우 (시나리오별 컴포넌트 협력)

### 3.1 게시글 목록 (`GET /api/teams/{teamId}/posts`)

```
PostController.list(userId, teamId, page, size)
  → PostService.list(userId, studyId=teamId, page, size)
    → (TX 시작, readOnly)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → assertActiveMember(studyId, userId)             // 비멤버 차단
        ⇡ 아니면 FORBIDDEN
    → Page<Post> = PostRepository.findAllByStudyIdAndNotDeleted(studyId, PageRequest)
    → authorUserIds = posts → memberId → user 이름 batch fetch
  ← PostListResponse(posts[], totalCount)
```

정렬: `created_at DESC` (최신순). 페이징: `page=0, size=20, max=100` (D-015 동일).
조회 조건: `study_id = :studyId AND is_deleted = 0`. NOTICE / FREE 구분 없이 한 리스트로 반환 (시트 응답 모양 정합).

작성자 이름: `Post.authorMemberId` → `StudyMember.userId` → `User.name`. batch fetch 로 N+1 회피.

### 3.2 게시글 생성 (`POST /api/teams/{teamId}/posts`)

```
PostController.create(userId, teamId, request) → 201
  → PostService.create(userId, studyId=teamId, command)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → caller = StudyMemberRepository.findActiveByStudyIdAndUserId(studyId, userId)
        ⇡ 없으면 FORBIDDEN
    → (command.type == NOTICE) → assertCanPostNotice(caller)
        ⇡ 아니면 FORBIDDEN ("공지 작성 권한이 없습니다.")
    → post = Post.create(studyId, caller.id, type, title, content)
    → PostRepository.save(post)
    → (TX 커밋)
  ← PostCreateResponse(postId, title, createdAt)
```

`assertCanPostNotice`: `study_role.can_post_notice = 1` 인 멤버만 NOTICE 가능 (D-020 §20.d). 검증은 `StudyMemberRepository.findActiveNoticeWriter` JPQL join 으로 일괄 처리.

`Post.create` 정적 팩토리: `is_deleted=0`, `created_at = now()`.

### 3.3 게시글 상세 (`GET /api/teams/{teamId}/posts/{postId}`)

```
PostController.detail(userId, teamId, postId)
  → PostService.detail(userId, studyId=teamId, postId)
    → (TX 시작, readOnly)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → assertActiveMember(studyId, userId)
        ⇡ 아니면 FORBIDDEN
    → post = PostRepository.findByIdAndStudyIdAndNotDeleted(postId, studyId)
        ⇡ 없으면 NOT_FOUND
    → authorName = User 이름 lookup (post.authorMemberId → userId)
  ← PostDetailResponse(...)
```

시트 명세 응답 필드: `postId / title / content / type / authorName / createdAt / updatedAt`. 댓글 목록은 별도 엔드포인트(§3.6) 책임 — 본 응답에 포함하지 않음 (시트 설명 "상세 내용과 댓글" 어휘 있으나 시트 응답 본문에 comments 배열 없음 → 본문 우선).

### 3.4 게시글 수정 (`PATCH /api/teams/{teamId}/posts/{postId}`)

```
PostController.update(userId, teamId, postId, request) → 200
  → PostService.update(userId, studyId=teamId, postId, command)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → caller = StudyMemberRepository.findActiveByStudyIdAndUserId(studyId, userId)
        ⇡ 없으면 FORBIDDEN
    → post = PostRepository.findByIdAndStudyIdAndNotDeletedForUpdate(postId, studyId)
        ⇡ 없으면 NOT_FOUND
    → (post.authorMemberId != caller.id) → FORBIDDEN ("게시글 수정 권한이 없습니다.")   // D-020 §20.h
    → post.update(command.title, command.content)          // 미전송 필드는 변경 없음
    → (TX 커밋)
  ← PostUpdateResponse(postId, title, updatedAt)
```

권한: **작성자 본인만** (D-020 §20.h). LEADER 라도 타인 게시글 수정 불가.
PATCH 시멘틱: 미전송 필드는 변경 없음, `null` 명시 전송은 `400 INVALID_INPUT` (D-015 동일).
수정 가능 필드: `title`, `content`. `type` (NOTICE/FREE) 은 수정 불가 (시트 명세 body 정합).

`Post.update(title, content)`: 도메인 메서드. `updated_at = now()`.

### 3.5 게시글 삭제 (`DELETE /api/teams/{teamId}/posts/{postId}`)

```
PostController.delete(userId, teamId, postId) → 204
  → PostService.delete(userId, studyId=teamId, postId)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → caller = StudyMemberRepository.findActiveByStudyIdAndUserId(studyId, userId)
        ⇡ 없으면 FORBIDDEN
    → post = PostRepository.findByIdAndStudyIdAndNotDeletedForUpdate(postId, studyId)
        ⇡ 없으면 NOT_FOUND
    → boolean isAuthor   = (post.authorMemberId == caller.id)
      boolean isLeader   = StudyMemberRepository.findActiveManager(studyId, userId).isPresent()
      if !(isAuthor || isLeader) → FORBIDDEN ("게시글 삭제 권한이 없습니다.")            // D-020 §20.h
    → post.softDelete()                                    // is_deleted=1, deleted_at=now()
    → (TX 커밋)
```

권한: **작성자 본인 + LEADER 모두 가능** (D-020 §20.h). LEADER 판정은 도메인 B 의 `findActiveManager` (= `can_manage_member`) 재사용. 시트 어휘는 "게시글 삭제 권한이 없습니다" 가 메시지.

응답: `204 No Content`. 시트는 `200 OK` 명시이나 도메인 A/B DELETE 일관성(204) 채택. 시트와 차이는 사소 — 별도 P-NNN 미등록, 시트는 코드 머지 후 채범수가 갱신.

soft delete: `is_deleted=1`, `deleted_at=now()`, row 보존 (D-020 §20.g). 댓글 cascade 처리: 게시글 삭제 시 댓글은 `is_deleted` 그대로 둠 (조회 쿼리에서 부모 게시글 deleted 조건으로 자연 비노출). 댓글 row 보존.

### 3.6 댓글 목록 (`GET /api/teams/{teamId}/posts/{postId}/comments`)

```
PostCommentController.list(userId, teamId, postId)
  → PostCommentService.list(userId, studyId=teamId, postId)
    → (TX 시작, readOnly)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → assertActiveMember(studyId, userId)
        ⇡ 아니면 FORBIDDEN
    → post = PostRepository.findByIdAndStudyIdAndNotDeleted(postId, studyId)
        ⇡ 없으면 NOT_FOUND
    → comments = PostCommentRepository.findAllByPostIdAndNotDeletedOrderByCreatedAtAsc(postId)
    → batch fetch 작성자 이름
  ← CommentListResponse(comments[], totalCount)
```

정렬: `created_at ASC` (자연스러운 대화 순서). 페이징 없음 — 게시글당 댓글 수가 적을 것이라 전수 반환. 추후 폭발하면 별도 결정으로 페이징 추가.

### 3.7 댓글 생성 (`POST /api/teams/{teamId}/posts/{postId}/comments`)

```
PostCommentController.create(userId, teamId, postId, request) → 201
  → PostCommentService.create(userId, studyId=teamId, postId, command)
    → (TX 시작)
    → StudyRepository.findByIdAndNotDeleted(studyId)
        ⇡ 없으면 NOT_FOUND
    → caller = StudyMemberRepository.findActiveByStudyIdAndUserId(studyId, userId)
        ⇡ 없으면 FORBIDDEN
    → post = PostRepository.findByIdAndStudyIdAndNotDeleted(postId, studyId)
        ⇡ 없으면 NOT_FOUND
    → comment = PostComment.create(studyId, postId, caller.id, content)
    → PostCommentRepository.save(comment)
    → (TX 커밋)
  ← CommentResponse(commentId, content, authorName, createdAt)
```

활성 멤버 누구나 작성 가능. 댓글에는 type 분기 없음.

### 3.8 댓글 수정 (`PATCH /api/teams/{teamId}/posts/{postId}/comments/{commentId}`)

```
PostCommentController.update(userId, teamId, postId, commentId, request) → 200
  → PostCommentService.update(userId, studyId=teamId, postId, commentId, command)
    → (TX 시작)
    → 동일 가드 (study/팀원/게시글 검증)
    → comment = PostCommentRepository.findByIdAndPostIdAndNotDeletedForUpdate(commentId, postId)
        ⇡ 없으면 NOT_FOUND
    → (comment.studyId != studyId) → NOT_FOUND
    → (comment.authorMemberId != caller.id) → FORBIDDEN ("댓글 수정 권한이 없습니다.")  // D-020 §20.h
    → comment.update(content)
  ← CommentResponse(commentId, content, updatedAt)        // 시트 응답 형 — authorName 생략
```

권한: **작성자 본인만**. LEADER 라도 타인 댓글 수정 불가 (D-020 §20.h).

### 3.9 댓글 삭제 (`DELETE /api/teams/{teamId}/posts/{postId}/comments/{commentId}`)

```
PostCommentController.delete(userId, teamId, postId, commentId) → 204
  → PostCommentService.delete(userId, studyId=teamId, postId, commentId)
    → 동일 가드
    → comment = PostCommentRepository.findByIdAndPostIdAndNotDeletedForUpdate(commentId, postId)
        ⇡ 없으면 NOT_FOUND
    → (comment.authorMemberId != caller.id) → FORBIDDEN ("댓글 삭제 권한이 없습니다.")  // D-020 §20.h
    → comment.softDelete()
```

권한: **작성자 본인만**. 게시글과 달리 LEADER 면제 없음 (D-020 §20.h 어휘 정합).

응답: `204 No Content` (시트 200 OK 와 차이, 도메인 A/B 패턴 정합).

---

## 4. Controller — 메서드 시그니처

### 4.1 PostController

`@RestController`, `@RequestMapping("/api/teams/{teamId}/posts")`, `@RequiredArgsConstructor`, `@Validated`.

```java
@GetMapping
public PostListResponse list(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @RequestParam(defaultValue = "0")  @Min(0)            int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100)  int size
);

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public PostCreateResponse create(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @Valid @RequestBody PostCreateRequest request
);

@GetMapping("/{postId}")
public PostDetailResponse detail(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long postId
);

@PatchMapping("/{postId}")
public PostUpdateResponse update(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long postId,
    @Valid @RequestBody PostUpdateRequest request
);

@DeleteMapping("/{postId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long postId
);
```

### 4.2 PostCommentController

`@RestController`, `@RequestMapping("/api/teams/{teamId}/posts/{postId}/comments")`, `@RequiredArgsConstructor`, `@Validated`.

```java
@GetMapping
public CommentListResponse list(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long postId
);

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public CommentResponse create(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long postId,
    @Valid @RequestBody CommentCreateRequest request
);

@PatchMapping("/{commentId}")
public CommentResponse update(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long postId,
    @PathVariable long commentId,
    @Valid @RequestBody CommentUpdateRequest request
);

@DeleteMapping("/{commentId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(
    @AuthenticationPrincipal CustomUserDetails principal,
    @PathVariable long teamId,
    @PathVariable long postId,
    @PathVariable long commentId
);
```

---

## 5. Service — public 메서드 시그니처

### 5.1 PostService

```java
@Transactional(readOnly = true)
public PostListResponse list(long callerUserId, long studyId, int page, int size);

@Transactional
public PostCreateResponse create(long callerUserId, long studyId, PostCreateCommand command);

@Transactional(readOnly = true)
public PostDetailResponse detail(long callerUserId, long studyId, long postId);

@Transactional
public PostUpdateResponse update(long callerUserId, long studyId, long postId, PostUpdateCommand command);

@Transactional
public void delete(long callerUserId, long studyId, long postId);
```

### 5.2 PostCommentService

```java
@Transactional(readOnly = true)
public CommentListResponse list(long callerUserId, long studyId, long postId);

@Transactional
public CommentResponse create(long callerUserId, long studyId, long postId, CommentCreateCommand command);

@Transactional
public CommentResponse update(long callerUserId, long studyId, long postId, long commentId, CommentUpdateCommand command);

@Transactional
public void delete(long callerUserId, long studyId, long postId, long commentId);
```

`*Command` 는 서비스 입력용 record (Sprint 3 패턴). 컨트롤러가 request DTO → command 변환.

---

## 6. Repository — 쿼리 메서드 시그니처

### 6.1 PostRepository

```java
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        select p from Post p
        where p.studyId = :studyId
          and p.isDeleted = false
        order by p.createdAt desc
    """)
    Page<Post> findAllByStudyIdAndNotDeleted(@Param("studyId") long studyId, Pageable pageable);

    @Query("""
        select p from Post p
        where p.id = :id
          and p.studyId = :studyId
          and p.isDeleted = false
    """)
    Optional<Post> findByIdAndStudyIdAndNotDeleted(@Param("id") long id, @Param("studyId") long studyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p from Post p
        where p.id = :id
          and p.studyId = :studyId
          and p.isDeleted = false
    """)
    Optional<Post> findByIdAndStudyIdAndNotDeletedForUpdate(@Param("id") long id, @Param("studyId") long studyId);
}
```

### 6.2 PostCommentRepository

```java
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    @Query("""
        select c from PostComment c
        where c.postId = :postId
          and c.isDeleted = false
        order by c.createdAt asc
    """)
    List<PostComment> findAllByPostIdAndNotDeletedOrderByCreatedAtAsc(@Param("postId") long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c from PostComment c
        where c.id = :id
          and c.postId = :postId
          and c.isDeleted = false
    """)
    Optional<PostComment> findByIdAndPostIdAndNotDeletedForUpdate(@Param("id") long id, @Param("postId") long postId);
}
```

### 6.3 StudyMemberRepository — 추가 쿼리

```java
// §3.2 NOTICE 작성 권한 검증 — 활성 멤버 + can_post_notice
@Query("""
    select sm from StudyMember sm, com.studymate.application.domain.StudyRole r
    where r.code = sm.roleCode
      and sm.studyId = :studyId
      and sm.userId  = :userId
      and sm.isActive = true
      and r.canPostNotice = true
""")
Optional<StudyMember> findActiveNoticeWriter(@Param("studyId") long studyId,
                                             @Param("userId") long userId);
```

기존 `findActiveByStudyIdAndUserId` (활성 멤버 일반 조회), `findActiveManager` (LEADER 판정, 도메인 B) 재사용.

### 6.4 StudyRole — 추가 매핑 필드

`canPostNotice` 매핑이 도메인 A 시점에서 미반영이면 추가. 도메인 A 의 `StudyRole` 엔티티에 `@Column(name = "can_post_notice") private boolean canPostNotice;` 보강.

---

## 7. Entity 필드

### 7.1 Post (신규)

```java
@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)         private long studyId;
    @Column(name = "author_member_id", nullable = false) private long authorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostType type;                               // NOTICE / FREE

    @Column(nullable = false, length = 300) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;

    @Column(name = "is_deleted", nullable = false) private boolean isDeleted;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    // 정적 팩토리
    public static Post create(long studyId, long authorMemberId, PostType type, String title, String content) {
        LocalDateTime now = LocalDateTime.now();
        Post p = new Post();
        p.studyId = studyId; p.authorMemberId = authorMemberId;
        p.type = type; p.title = title; p.content = content;
        p.isDeleted = false; p.createdAt = now; p.updatedAt = now;
        return p;
    }

    // 도메인 메서드
    public void update(String title, String content) {
        if (title   != null) this.title   = title;
        if (content != null) this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        if (this.isDeleted) throw new IllegalStateException("already deleted");
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.updatedAt = this.deletedAt;
    }
}
```

### 7.2 PostType (enum, 신규)

```java
public enum PostType { NOTICE, FREE }
```

### 7.3 PostComment (신규)

```java
@Entity
@Table(name = "post_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)         private long studyId;
    @Column(name = "post_id", nullable = false)          private long postId;
    @Column(name = "author_member_id", nullable = false) private long authorMemberId;

    @Column(nullable = false, columnDefinition = "TEXT") private String content;

    @Column(name = "is_deleted", nullable = false) private boolean isDeleted;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public static PostComment create(long studyId, long postId, long authorMemberId, String content) {
        LocalDateTime now = LocalDateTime.now();
        PostComment c = new PostComment();
        c.studyId = studyId; c.postId = postId; c.authorMemberId = authorMemberId;
        c.content = content; c.isDeleted = false; c.createdAt = now; c.updatedAt = now;
        return c;
    }

    public void update(String content) {
        this.content   = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        if (this.isDeleted) throw new IllegalStateException("already deleted");
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.updatedAt = this.deletedAt;
    }
}
```

---

## 8. Request / Response DTO

### 8.1 Request DTO

```java
public record PostCreateRequest(
    @NotBlank @Size(min = 1, max = 300) String title,
    @NotBlank @Size(min = 1, max = 5000) String content,
    @NotNull PostType type
) {}

public record PostUpdateRequest(
    @Size(min = 1, max = 300)  String title,    // 미전송 = 변경 없음
    @Size(min = 1, max = 5000) String content
) {}

public record CommentCreateRequest(
    @NotBlank @Size(min = 1, max = 1000) String content
) {}

public record CommentUpdateRequest(
    @NotBlank @Size(min = 1, max = 1000) String content
) {}
```

`title` 길이 상한 300 = DB `VARCHAR(300)` 정합.
`content` 길이 상한 5000 = 본 스프린트 가정. DB `TEXT` 라 향후 확장 가능. (P-010 후보 — 본 스프린트는 5000 채택.)
`comment.content` 길이 상한 1000 = 댓글 가정.

### 8.2 Response DTO

```java
public record PostSummaryResponse(
    long          postId,
    String        title,
    PostType      type,
    String        authorName,
    LocalDateTime createdAt
) {}

public record PostListResponse(
    List<PostSummaryResponse> posts,
    long                       totalCount
) {}

public record PostCreateResponse(
    long          postId,
    String        title,
    LocalDateTime createdAt
) {}

public record PostDetailResponse(
    long          postId,
    String        title,
    String        content,
    PostType      type,
    String        authorName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

public record PostUpdateResponse(
    long          postId,
    String        title,
    LocalDateTime updatedAt
) {}

public record CommentResponse(
    long          commentId,
    String        content,
    String        authorName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

public record CommentListResponse(
    List<CommentResponse> comments,
    int                    totalCount
) {}
```

`totalCount` = `Page.getTotalElements()` (목록) / `list.size()` (댓글). 페이징 응답에 `page`/`size` 미포함 (시트 응답 모양 정합 — 추후 필요 시 별도 결정).

---

## 9. ErrorCode enum

기존 `com.studymate.common.exception.ErrorCode` 재사용. **신규 ErrorCode 없음**.

| 상황 | ErrorCode | HTTP | 메시지 오버라이드 |
|---|---|---|---|
| 스터디 미존재 / 게시글 미존재 / 댓글 미존재 | `NOT_FOUND` | 404 | 기본 / "게시글을 찾을 수 없습니다." / "댓글을 찾을 수 없습니다." |
| 비활성 멤버 (팀 접근 권한 X) | `FORBIDDEN` | 403 | "팀 접근 권한이 없습니다." |
| NOTICE 작성 권한 없음 (`can_post_notice=0`) | `FORBIDDEN` | 403 | "공지 작성 권한이 없습니다." |
| 게시글 수정 - 작성자 아님 | `FORBIDDEN` | 403 | "게시글 수정 권한이 없습니다." |
| 게시글 삭제 - 작성자 아님 + LEADER 아님 | `FORBIDDEN` | 403 | "게시글 삭제 권한이 없습니다." |
| 댓글 수정 - 작성자 아님 | `FORBIDDEN` | 403 | "댓글 수정 권한이 없습니다." |
| 댓글 삭제 - 작성자 아님 | `FORBIDDEN` | 403 | "댓글 삭제 권한이 없습니다." |
| 입력 형식 위반 (Bean Validation) | `INVALID_INPUT` | 400 | 기본 |

D-020 §20.k 의 신규 4건은 도메인 A 에서 모두 등록 완료. 본 도메인 신규 0건.

---

## 10. 동시성 / 트랜잭션 정책

### 10.1 비관적 락 (PESSIMISTIC_WRITE) 적용 지점

| 메서드 | study row | post row | comment row |
|---|---|---|---|
| `Post.list` / `Post.detail` / `Comment.list` | — (readOnly) | — | — |
| `Post.create` / `Comment.create` | — | — | — |
| `Post.update` / `Post.delete` | — | ✅ `findByIdAndStudyIdAndNotDeletedForUpdate` | — |
| `Comment.update` / `Comment.delete` | — | — | ✅ `findByIdAndPostIdAndNotDeletedForUpdate` |

`study` row 락 **사용하지 않음**. 사유:
- 게시판은 `study.current_member_count` / `status` 등 정원/마감 흐름과 무관.
- D-020 §20.c 의 락 대상에 게시판 메서드 미포함 (apply/approve/reject/kick/leave 만).

`post` row 락:
- 두 작성자(불가) 또는 작성자 + LEADER 가 동시에 같은 post 를 수정/삭제 시도 → 한 쪽 락 대기 → 두 번째 호출 `NOT_FOUND` (이미 삭제) 또는 권한 재검증.

`comment` row 락:
- 작성자 본인이 두 디바이스에서 동시 수정/삭제 → 직렬화.

### 10.2 트랜잭션 경계

- 메서드 1건 = 트랜잭션 1건.
- 조회 (`list` / `detail`) 는 `readOnly = true`.
- 쓰기 메서드는 단일 row UPDATE 위주 — 멀티 row 변경 없음.

### 10.3 DB 마이그레이션

**변경 없음** (D-020 §20.l). 기존 `post` / `post_comment` 테이블 그대로 사용.

JPA 신규 매핑: `Post`, `PostComment` 엔티티 (§7). `StudyRole.canPostNotice` 매핑 보강 (§6.4).

### 10.4 동시성 회귀 테스트

D-009 / D-020 §20.c 기조 유지: 코드 락만으로 닫는다. 통합 멀티스레드 회귀 테스트 작성하지 않음.

---

## 11. Trade-off Analysis

### 11.1 패키지 = 신규 `post` vs `study` 흡수
- **채택**: `com.studymate.post` 신규.
- 비용: 신규 패키지 1개.
- 대안: `com.studymate.study` 흡수.
- 근거: `Post` / `PostComment` 가 `Study` 라이프사이클의 부분집합 아님. 도메인 A(`application`) 와 동등한 격으로 분리.

### 11.2 게시글 상세에 댓글 포함 vs 별도 엔드포인트
- **채택**: 별도 엔드포인트 (시트 응답 본문 정합).
- 비용: 클라이언트가 2회 호출.
- 대안: 상세 응답에 `comments[]` 포함.
- 근거: 시트 명세의 상세 응답 본문에 `comments` 배열 없음. 페이징 확장 가능성 (§3.6) 위해서도 분리가 유리.

### 11.3 댓글 페이징 = 미적용 vs 적용
- **채택**: 미적용. 전수 반환.
- 비용: 한 게시글에 댓글 폭주 시 응답 비대.
- 대안: page/size 페이징.
- 근거: 학습용 스터디 게시판 댓글 수 적을 가정. 폭주 발생 시 별도 결정으로 페이징 추가.

### 11.4 게시글 삭제 cascade = 댓글 보존 vs 댓글 동시 soft delete
- **채택**: 댓글 보존, 부모 게시글 deleted 로 자연 비노출.
- 비용: 댓글 row 누적.
- 대안: 게시글 삭제 시 자식 댓글 일괄 `is_deleted=1`.
- 근거: 향후 게시글 복원(soft delete 의 본 취지) 시 댓글도 함께 복원 가능. 부모 deleted 조건으로 비노출 충분.

### 11.5 게시글 수정 권한 = 작성자만 vs 작성자 + LEADER
- **채택**: 작성자만 (D-020 §20.h 정합).
- 비용: LEADER 가 부적절한 게시글 수정 못 함 → 삭제만 가능.
- 대안: LEADER 도 수정 가능.
- 근거: 작성자 의도 보호. 부적절한 내용은 삭제 권한으로 충분.

### 11.6 DELETE 응답 코드 = 204 vs 시트의 200 OK
- **채택**: 204 No Content (도메인 A/B 정합).
- 비용: 시트 명세와 표면적 차이.
- 대안: 200 OK 본문 없이.
- 근거: 시맨틱상 본문 없는 삭제 응답은 204 가 표준. 시트는 코드 머지 후 갱신 예정.

---

## 12. D-020 처리 결과 (2026-05-27)

| 결정 | 본 문서 반영 위치 |
|---|---|
| D-020 §20.a 도메인 C 9 엔드포인트 | §1 (패키지) / §4 (Controller) / §5 (Service) |
| D-020 §20.d NOTICE 권한 (`can_post_notice`) | §3.2 / §6.3 / §6.4 |
| D-020 §20.g soft delete (post/comment) | §3.5 / §3.9 / §7 / §11.4 |
| D-020 §20.h 수정/삭제 권한 (게시글 작성자, 게시글 삭제 + LEADER, 댓글 작성자만) | §3.4 / §3.5 / §3.8 / §3.9 / §11.5 |
| D-020 §20.l DB 스키마 변경 없음 | §10.3 |

---

## 13. 다음 단계로 넘기는 입력 체크리스트

테스트 작성 진입 시 본 문서 + 아래 항목 입력:

- [x] 9개 엔드포인트 시그니처 (§4)
- [x] Service public 메서드 시그니처 (§5)
- [x] Repository 쿼리 메서드 시그니처 (§6)
- [x] Entity 필드 + 도메인 메서드 (§7)
- [x] Request / Response DTO 모양 (§8)
- [x] ErrorCode 매핑 — 신규 0건 (§9)
- [x] 동시성/트랜잭션 정책 (§10.1, §10.2)
- [x] DB 마이그레이션 — 없음 (§10.3)
- [ ] 테스트 플랜 (`docs/post-test-plan.md`) — 다음 단계 산출물

테스트 매핑 후보 ID (SRS 검사기준 ↔ 본 설계):
- RE-SF4-02 (팀 게시판 글 작성) ↔ §3.2 / `POST .../posts`
- RE-SF4-03 (팀 게시판 댓글) ↔ §3.6 ~ §3.9
- 시트 명세 게시글 CRUD 5건 ↔ §3.1 ~ §3.5
- 시트 명세 댓글 CRUD 4건 ↔ §3.6 ~ §3.9
