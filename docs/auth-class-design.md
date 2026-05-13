# Sprint 2 — 인증 도메인 클래스 설계

> **Scope.** Sprint 2 인증 6개 엔드포인트 구현을 위한 클래스 토폴로지 / 메서드 시그니처 / DTO·Entity 필드 / 에러 코드.
>
> **목적.** 이 문서의 결과물이 다음 단계(테스트 작성)의 입력이 된다. 따라서 "테스트가 작성 가능하도록 외부에서 보이는 시그니처와 계약"을 픽싱하는 것까지만 한다. 각 메서드 내부 구현 디테일(쿼리/알고리즘/예외 변환 순서 등)은 의도적으로 비워둔다.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 작성). 2차: 시서경(스키마 추가 P-001), 최평화(클라이언트 흐름 확인). Reviewer는 D-001~D-005 + API 명세 + SRS와 본 문서가 일관되는지를 본다.
>
> **전제 결정.** `docs/decisions-log.md` D-001 ~ D-005, `docs/claude-context.md` §1~§3.

---

## 1. 패키지 토폴로지

기준 패키지: `com.studymate.auth`
공통 패키지: `com.studymate.common` (ErrorCode, GlobalExceptionHandler, ApiErrorResponse 등 다른 도메인과 공유)

```
com.studymate.auth
├── controller
│   └── AuthController
├── service
│   ├── AuthService                   // 회원가입 / 로그인 / 로그아웃 / 토큰 재발급 (퍼사드)
│   ├── EmailVerificationService      // 인증 코드 발송 + 검증 + 가입 시 소비
│   └── TokenService                  // JWT 발급/검증 + RefreshToken 저장/회전/폐기
├── domain
│   ├── User                          // app_user
│   ├── EmailVerification             // email_verification (P-001로 신규 추가)
│   └── RefreshToken                  // refresh_token (P-001로 신규 추가)
├── repository
│   ├── UserRepository
│   ├── EmailVerificationRepository
│   └── RefreshTokenRepository
├── dto
│   ├── request
│   │   ├── SignupRequest
│   │   ├── LoginRequest
│   │   ├── SendEmailCodeRequest
│   │   ├── VerifyEmailCodeRequest
│   │   └── RefreshTokenRequest
│   └── response
│       ├── SignupResponse
│       ├── LoginResponse
│       └── TokenRefreshResponse
├── security
│   ├── SecurityConfig                // 필터 체인, 패스워드 인코더 빈
│   ├── JwtAuthenticationFilter       // OncePerRequestFilter
│   ├── JwtTokenProvider              // 토큰 생성/파싱 (저수준)
│   ├── CustomUserDetails
│   └── CustomUserDetailsService
├── mail
│   ├── EmailSender                   // 인터페이스 (테스트시 fake 주입)
│   └── SmtpEmailSender               // 운영 구현
└── exception
    └── AuthException                 // ErrorCode + cause 보유
```

`com.studymate.common`:
```
com.studymate.common
├── exception
│   ├── ErrorCode                     // enum, HTTP status + 코드 문자열
│   ├── BusinessException             // 모든 도메인 공통 베이스
│   └── GlobalExceptionHandler        // @RestControllerAdvice
└── dto
    └── ApiErrorResponse              // {code, message}
```

`AuthException extends BusinessException`. 도메인 예외는 컨트롤러까지 그대로 던지고, `GlobalExceptionHandler`가 단일 지점에서 HTTP 응답으로 변환.

---

## 2. 클래스별 책임 + 의존성

### 2.1 AuthController
- 책임: HTTP 입출력 어댑터. Request DTO ↔ Service 호출 ↔ Response DTO. 비즈니스 로직 없음.
- 의존: `AuthService`, `EmailVerificationService`.
- 라우팅: `@RequestMapping("/api/auth")`.

### 2.2 AuthService
- 책임: 회원가입(`signup`), 로그인(`login`), 로그아웃(`logout`), 토큰 재발급(`refresh`)의 도메인 트랜잭션 조립.
- 의존: `UserRepository`, `EmailVerificationService` (인증 완료 여부 검증/소비), `TokenService` (토큰 발급/회전/폐기), `PasswordEncoder` (BCrypt 빈).

### 2.3 EmailVerificationService
- 책임: 인증 코드 row 생성·코드 발송, 코드 검증(`verify`), 가입 시점에 "유효 인증 레코드 존재" 확인 + 소비. D-005에서 정의한 흐름의 단일 진입점.
- 의존: `EmailVerificationRepository`, `EmailSender`, `Clock` (테스트 격리), 코드 생성기(내부 `SecureRandom` 기반).
- 정책 상수 (D-008 확정): 코드 길이 6자리 숫자 / TTL 10분 / 시도 횟수 상한 5회 / 같은 이메일 직전 발송 후 60초 쿨다운.

### 2.4 TokenService
- 책임: Access·Refresh JWT 발급, RefreshToken DB row 저장(token_hash) + 만료/폐기 관리, **회전(rotation)** 시 이전 토큰 revoke + 신규 토큰 발급, 재사용 감지(reuse detection) 시 해당 사용자의 refresh 일괄 폐기. Logout 시 refresh row revoke.
- 의존: `JwtTokenProvider`, `RefreshTokenRepository`, `Clock`.
- 토큰 정책 (D-001):
  - Access JWT: 30분, HS256, claim = `{sub: userId, email, iat, exp, jti}`
  - Refresh JWT: 14일, HS256, claim = `{sub: userId, iat, exp, jti}`. 본문 자체는 stateless하지만 DB의 `refresh_token.token_hash` (= SHA-256(token)) 와 매칭되어야 유효.

### 2.5 JwtTokenProvider (security)
- 저수준 JWT 생성/파싱. 비즈니스 규칙 없음. `TokenService`만이 호출.

### 2.6 JwtAuthenticationFilter
- `Authorization: Bearer <accessToken>` → `JwtTokenProvider.parse` → `CustomUserDetailsService.loadUserByUserId` → `SecurityContextHolder` 주입.
- 토큰 없거나 파싱 실패 시 그대로 통과 (entry point가 401 처리).

### 2.7 SecurityConfig
- `SecurityFilterChain` 빈. CSRF off, 세션 stateless, public path 화이트리스트 (`/api/auth/**`), 그 외 `authenticated()`.
- `PasswordEncoder` 빈 = `BCryptPasswordEncoder(strength=12)`.

### 2.8 GlobalExceptionHandler (common)
- `BusinessException` → `ApiErrorResponse(code, message)` + `ErrorCode.httpStatus()`.
- `MethodArgumentNotValidException` → `INVALID_INPUT` 매핑 + 첫 위반 메시지.
- `AuthenticationException` (Spring Security) → 컨텍스트별로 `UNAUTHORIZED` 매핑.

---

## 3. 데이터 플로우 (시나리오별 컴포넌트 협력)

테스트 시나리오를 그대로 깎아낼 수 있도록 4개 핵심 흐름의 호출 순서를 글로 픽싱한다. `→` = 호출, `⇡` = 예외 발생.

### 3.1 이메일 인증 코드 발송 + 검증 (가입 사전 단계)

```
[Client] POST /api/auth/email/send-code  {email}
   → AuthController.sendEmailCode
      → @Valid 위반        ⇡ MethodArgumentNotValidException → INVALID_INPUT (400)
      → EmailVerificationService.sendCode(email)
         → UserRepository.existsByEmailAndIsDeletedFalse(email)
            ⇡ true         → AuthException(CONFLICT, 409)
         → repository.findLastSentAtByEmail(email)
            ⇡ now - last < 60s → AuthException(INVALID_INPUT, "잠시 후 다시 시도해주세요.")  // P-008
         → SecureRandom 6자리 코드 생성, SHA-256 해시
         → EmailVerification.new(email, codeHash, expiresAt = now + 10m)
         → repository.save(...)
         → emailSender.send(email, plainCode)
   → 200 OK (body 없음)

[Client] POST /api/auth/email/verify  {email, code}
   → AuthController.verifyEmailCode
      → EmailVerificationService.verifyCode(email, code)
         → repository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)  // PESSIMISTIC_WRITE
            ⇡ empty        → AuthException(INVALID_EMAIL_CODE, 400)
         → ev.expiresAt <= now ⇡ AuthException(EXPIRED_EMAIL_CODE, 400)
         → ev.attemptCount >= 5 ⇡ AuthException(INVALID_EMAIL_CODE, 400)
         → SHA-256(input) != ev.codeHash
            → ev.incrementAttempt(); save
            ⇡ AuthException(INVALID_EMAIL_CODE, 400)
         → ev.markVerified(now); save
   → 200 OK (body 없음)
```

### 3.2 회원가입

```
[Client] POST /api/auth/signup  {email, password, name}
   → AuthController.signup
      → @Valid 위반 ⇡ INVALID_INPUT
      → AuthService.signup(req)
         → emailVerificationService.consumeVerifiedRecord(req.email)
            → repository.findUsableForSignup(email, now)  // PESSIMISTIC_WRITE
               ⇡ empty → AuthException(EMAIL_NOT_VERIFIED, 400)   // P-006
            → ev.markConsumed(now); save
         → userRepository.existsByEmailAndIsDeletedFalse(email)
            ⇡ true → AuthException(CONFLICT, 409)
         → User.create(email, passwordEncoder.encode(req.password), req.name)
         → userRepository.save(user)   // unique 제약 위반 시 DataIntegrityViolationException → CONFLICT
   → 201 Created  {userId, email, name}
```

(자동 로그인 토큰 발급은 명세 응답 형식에 포함되어 있지 않으므로 본 단계에서는 발급하지 않는다. 클라이언트가 별도로 `/api/auth/login`을 호출. — D-004 흐름 메모와 일치)

### 3.3 로그인

```
[Client] POST /api/auth/login  {email, password}
   → AuthController.login
      → AuthService.login(req)
         → userRepository.findByEmailAndIsDeletedFalse(email)
            ⇡ empty → AuthException(INVALID_CREDENTIALS, 401)
         → passwordEncoder.matches(req.password, user.passwordHash)
            ⇡ false → AuthException(INVALID_CREDENTIALS, 401)
         → tokenService.issueTokenPair(user.id, user.email)
            → JwtTokenProvider.createAccessToken(...)
            → JwtTokenProvider.createRefreshToken(...)
            → refreshTokenRepository.save(RefreshToken.new(userId, sha256(refresh), exp))
            → return TokenPair
   → 200 OK  {accessToken, refreshToken, userId}
```

### 3.4 토큰 재발급 (회전 + 재사용 감지)

```
[Client] POST /api/auth/token/refresh  {refreshToken}
   → AuthController.refreshToken
      → AuthService.refresh(refreshToken)
         → tokenService.rotate(refreshToken)
            → JwtTokenProvider.parse(refreshToken)
               ⇡ ExpiredJwtException     → AuthException(TOKEN_EXPIRED, 401)
               ⇡ JwtException 그 외       → AuthException(INVALID_TOKEN, 401)
            → repository.findByTokenHash(sha256(refreshToken))
               ⇡ empty → AuthException(INVALID_TOKEN, 401)
            → row.isActive(now)?
               ⇡ revokedAt != null      // 재사용 감지
                  → revokeAllByUserId(row.userId, now)
                  ⇡ AuthException(INVALID_TOKEN, 401)
               ⇡ expired                → AuthException(TOKEN_EXPIRED, 401)
            → revokeById(row.id, now); affected == 0 ⇡ INVALID_TOKEN  // 동시 회전 race
            → 신규 pair 발급 (3.3과 동일 흐름)
   → 200 OK  {accessToken, refreshToken}
```

### 3.5 로그아웃 (D-007: 단일 세션)

```
[Client] POST /api/auth/logout  (Authorization: Bearer <access>)  {refreshToken}
   → JwtAuthenticationFilter가 SecurityContext에 CustomUserDetails 주입
   → AuthController.logout(@AuthenticationPrincipal CustomUserDetails, @Valid @RequestBody RefreshTokenRequest)
      → 인증 실패 ⇡ AuthenticationException → UNAUTHORIZED (401)
      → @Valid 위반 ⇡ MethodArgumentNotValidException → INVALID_INPUT (400)
      → AuthService.logout(principal.userId, request.refreshToken)
         → tokenService.revokeOne(principal.userId, request.refreshToken)
            → JwtTokenProvider.parse(refreshToken)
               ⇡ ExpiredJwtException → AuthException(TOKEN_EXPIRED, 401)
               ⇡ JwtException 그 외   → AuthException(INVALID_TOKEN, 401)
            → refreshTokenRepository.findByTokenHash(sha256(refreshToken))
               ⇡ empty → AuthException(INVALID_TOKEN, 401)
            → row.userId != principal.userId ⇡ AuthException(INVALID_TOKEN, 401)   // 소유권 검증
            → refreshTokenRepository.revokeById(row.id, now)   // affected=0(이미 revoked) 도 idempotent OK
   → 200 OK (body 없음)
```

D-007: 단일 세션 로그아웃 — 입력 refresh row 한 건만 폐기. 동일 사용자의 다른 디바이스 세션은 그대로 유지됨. (재사용 감지로 인한 전 디바이스 일괄 폐기는 `rotate` 내부 경로에서만 발동.)

---

## 4. AuthController — 메서드 시그니처

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request);

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request);

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public void logout(@AuthenticationPrincipal CustomUserDetails principal,
                       @Valid @RequestBody RefreshTokenRequest request);
    // D-007 (P-007 closure): 단일 세션 로그아웃. principal.userId 와 일치하는 refresh row 1건만 폐기.
    // API 명세 시트는 현재 Request Body 칸이 "-" 로 되어 있어 {refreshToken} 으로 갱신해야 함 (외부 적용 대기).

    @PostMapping("/email/send-code")
    public void sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request);

    @PostMapping("/email/verify")
    public void verifyEmailCode(@Valid @RequestBody VerifyEmailCodeRequest request);

    @PostMapping("/token/refresh")
    public TokenRefreshResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request);
}
```

반환 타입은 `ResponseEntity<...>` 대신 단순 DTO + `@ResponseStatus`로 통일 (테스트 작성 시 jsonPath 비교가 단순해짐).

---

## 5. Service — public 메서드 시그니처

### 5.1 AuthService

```java
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    /**
     * @throws AuthException CONFLICT (이메일 중복)
     * @throws AuthException EMAIL_NOT_VERIFIED (D-005: 유효한 인증 레코드 없음 — P-006 결정 시 코드명 확정)
     */
    public SignupResponse signup(SignupRequest request);

    /**
     * @throws AuthException INVALID_CREDENTIALS (이메일 미존재 OR 비밀번호 불일치 — 두 케이스 동일 메시지)
     */
    public LoginResponse login(LoginRequest request);

    /**
     * 단일 세션 로그아웃 (D-007): 인증된 사용자의 입력 refresh 토큰 1건만 폐기.
     * Access는 stateless라 별도 처리 없음.
     * @throws AuthException TOKEN_EXPIRED / INVALID_TOKEN (소유권 불일치 포함)
     */
    public void logout(long userId, String refreshToken);

    /**
     * @throws AuthException TOKEN_EXPIRED (만료)
     * @throws AuthException INVALID_TOKEN (서명/포맷 불일치, DB 미존재, 이미 revoked)
     *
     * 재사용 감지: 입력 refresh가 이미 revoked인데 다시 들어오면 → 해당 user_id의 모든 활성 refresh 폐기 + INVALID_TOKEN.
     */
    public TokenRefreshResponse refresh(String refreshToken);
}
```

### 5.2 EmailVerificationService

```java
@Service
@Transactional
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository repository;
    private final EmailSender emailSender;
    private final Clock clock;

    /**
     * @throws AuthException INVALID_INPUT (이메일 형식 위반 — controller 단에서 우선 검증)
     * @throws AuthException CONFLICT (이미 가입된 이메일)
     */
    public void sendCode(String email);

    /**
     * @throws AuthException EXPIRED_EMAIL_CODE
     * @throws AuthException INVALID_EMAIL_CODE (코드 불일치 / 시도 횟수 초과 / 해당 이메일 인증 row 없음)
     */
    public void verifyCode(String email, String code);

    /**
     * 가입 트랜잭션 내부에서 호출. 유효(미만료, verified, 미사용) 인증 레코드를 찾아 "사용 처리" 후 반환.
     * @throws AuthException EMAIL_NOT_VERIFIED  (P-006 결정 시 확정)
     */
    public void consumeVerifiedRecord(String email);
}
```

### 5.3 TokenService

```java
@Service
@Transactional
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /** 신규 발급 (login, signup-자동로그인 시) */
    public TokenPair issueTokenPair(long userId, String email);

    /** 회전: 입력 refresh를 검증 + revoke + 신규 pair 발급. */
    public TokenPair rotate(String refreshToken);

    /**
     * 단일 세션 로그아웃 (D-007): 입력 refresh 한 건만 폐기.
     * 내부에서 token 파싱 → token_hash 매칭 → row.userId == userId 검증 후 revokeById.
     * @throws AuthException TOKEN_EXPIRED / INVALID_TOKEN
     */
    public void revokeOne(long userId, String refreshToken);

    /** 재사용 감지 시 호출: 해당 user 의 모든 활성 refresh 일괄 폐기. rotate 내부에서만 사용 (외부 노출 X). */
    // private void revokeAllByUserId(long userId);

    /** 값 객체 */
    public record TokenPair(String accessToken, String refreshToken) {}
}
```

### 5.4 PasswordEncoder
- Spring Security 기본 빈을 그대로 사용. `SecurityConfig`에 `@Bean BCryptPasswordEncoder(12)`.

---

## 6. Repository — 쿼리 메서드 시그니처

JPA Repository. 복잡한 쿼리는 `@Query` 또는 `@Modifying`. 모든 시그니처는 그대로 픽싱 (테스트가 mocking 대상).

### 6.1 UserRepository
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailAndIsDeletedFalse(String email);
    boolean existsByEmailAndIsDeletedFalse(String email);
}
```

### 6.2 EmailVerificationRepository
```java
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** verify 단계: 가장 최근 미만료/미소비 row */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(String email);

    /** signup 단계: verified_at IS NOT NULL AND consumed_at IS NULL AND expires_at > now */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select e from EmailVerification e
           where e.email = :email
             and e.verifiedAt is not null
             and e.consumedAt is null
             and e.expiresAt > :now
           order by e.verifiedAt desc
           """)
    Optional<EmailVerification> findUsableForSignup(String email, Instant now);

    /** 쿨다운 체크용: 마지막 발송 시각 */
    Optional<Instant> findLastSentAtByEmail(String email);
}
```

### 6.3 RefreshTokenRepository
```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
    int revokeAllByUserId(long userId, Instant now);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.id = :id and r.revokedAt is null")
    int revokeById(long id, Instant now);
}
```

---

## 7. Entity 필드

### 7.1 `User` (table `app_user`, 기존 SQL 그대로)
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK, auto | |
| email | String | unique, not null, len ≤ 255 | |
| passwordHash | String | not null | BCrypt 결과 |
| name | String | not null, len ≤ 100 | |
| isEmailVerified | boolean | not null, default false | signup 시 true 박음 |
| isDeleted | boolean | not null, default false | soft delete |
| createdAt | Instant | not null | |
| updatedAt | Instant | not null | |

생성자: `User.create(email, passwordHash, name)` 정적 팩토리 — `isEmailVerified=true` (D-005에 의해 가입 시점에는 무조건 true), `isDeleted=false`.

### 7.2 `EmailVerification` (P-001 신규)
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK | |
| email | String | not null, indexed | |
| codeHash | String | not null | SHA-256(code) — 평문 저장 금지 |
| expiresAt | Instant | not null | createdAt + 10m |
| verifiedAt | Instant | nullable | verify 성공 시 set |
| consumedAt | Instant | nullable | signup에서 사용 시 set |
| attemptCount | int | not null, default 0 | verify 시도 누적 |
| createdAt | Instant | not null | |

도메인 메서드:
- `markVerified(Instant now)` — `verifiedAt`이 null이고 `expiresAt > now`일 때만.
- `incrementAttempt()` — `attemptCount++`.
- `markConsumed(Instant now)` — `consumedAt` set, idempotent.
- `isUsableForSignup(Instant now)` — `verifiedAt != null && consumedAt == null && expiresAt > now`.

### 7.3 `RefreshToken` (P-001 신규)
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK | |
| userId | Long | not null, FK app_user.id | |
| tokenHash | String | unique, not null | SHA-256(refreshJwt) |
| expiresAt | Instant | not null | issuedAt + 14d |
| revokedAt | Instant | nullable | rotation/logout 시 set |
| createdAt | Instant | not null | |

도메인 메서드:
- `revoke(Instant now)` — `revokedAt = now` (이미 revoked면 변경 없음).
- `isActive(Instant now)` — `revokedAt == null && expiresAt > now`.

---

## 8. Request / Response DTO

모든 Request DTO는 `record`. Bean Validation 사용. 응답 형식은 명세 시트 그대로.

### 8.1 Request

```java
public record SignupRequest(
    @Email @NotBlank @Size(max = 255) String email,
    @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}$",
                      message = "비밀번호는 영문과 숫자를 각각 1자 이상 포함하여 8~20자여야 합니다.")
    String password,
    @NotBlank @Size(max = 100) String name
) {}

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password
) {}

public record SendEmailCodeRequest(
    @Email @NotBlank @Pattern(regexp = ".+@university\\.ac\\.kr$",
                              message = "학교 이메일만 사용 가능합니다.")
    String email
) {}

public record VerifyEmailCodeRequest(
    @Email @NotBlank String email,
    @NotBlank @Size(min = 6, max = 6) String code
) {}

public record RefreshTokenRequest(
    @NotBlank String refreshToken
) {}
```

비밀번호 정규식 근거: SRS RE-SF1-01 ("영문과 숫자를 각각 1자 이상 포함하며, 8자 이상 20자 이하"). 특수문자 허용 여부는 SRS에 미명시 → 보수적으로 영문+숫자만 허용. (필요 시 신규 결정으로 명문화)

학교 이메일 도메인은 SRS상 `@university.ac.kr`로 표기되어 있음. 실제 학교 도메인은 배포 시 `application.yml` 프로퍼티로 추출 가능하지만 본 단계에서는 정규식 하드코딩 그대로.

### 8.2 Response

```java
public record SignupResponse(long userId, String email, String name) {}

public record LoginResponse(String accessToken, String refreshToken, long userId) {}

public record TokenRefreshResponse(String accessToken, String refreshToken) {}
```

`SendEmailCodeResponse` / `VerifyEmailCodeResponse` / `LogoutResponse` 는 명세상 본문 없음 → 컨트롤러 반환 `void`.

### 8.3 ApiErrorResponse (공통)
```java
public record ApiErrorResponse(String code, String message) {}
```

---

## 9. ErrorCode enum

명세 시트의 인증 6개 엔드포인트에서 등장하는 에러 + D-005/P-006 보강을 모두 모음. 동일 코드는 한 줄.

```java
public enum ErrorCode {
    // 4xx — 클라이언트 입력
    INVALID_INPUT             (HttpStatus.BAD_REQUEST,  "입력값 형식이 올바르지 않습니다."),
    INVALID_EMAIL_CODE        (HttpStatus.BAD_REQUEST,  "인증 코드가 올바르지 않습니다."),
    EXPIRED_EMAIL_CODE        (HttpStatus.BAD_REQUEST,  "인증 코드가 만료되었습니다."),

    // 401
    UNAUTHORIZED              (HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자입니다."),
    INVALID_CREDENTIALS       (HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED             (HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),
    INVALID_TOKEN             (HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // 409
    CONFLICT                  (HttpStatus.CONFLICT,     "이미 가입된 이메일입니다."),

    // 보강 (P-006: 명세에 없으나 D-005에 의해 필요)
    EMAIL_NOT_VERIFIED        (HttpStatus.BAD_REQUEST,  "이메일 인증이 완료되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
```

`AuthException(ErrorCode code, String overrideMessage)` 생성자로 메시지 오버라이드 가능. `GlobalExceptionHandler`가 `code.httpStatus()`로 응답 status 결정.

---

## 10. 동시성 / 트랜잭션 정책 (D-003 적용 위치)

| 시나리오 | 위치 | 전략 |
|---|---|---|
| 같은 이메일로 동시 signup | DB unique 제약 (`uq_app_user_email`) + `DataIntegrityViolationException` 캐치 → `CONFLICT`. | 추가 락 불필요 |
| email_verification verify 중 attempt_count race | `EmailVerificationService.verifyCode` 트랜잭션 내에서 `findTop... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`). | 비관적 락 |
| signup이 verified row를 소비하는 race | `EmailVerificationService.consumeVerifiedRecord`도 비관적 락으로 row 잡고 `consumedAt` set. signup 전체 트랜잭션 안에 포함. | 비관적 락 + 같은 트랜잭션 |
| Refresh rotation 동시 호출 | `TokenService.rotate` 트랜잭션 내에서 `findByTokenHash` 후 `revokeById(id, now)`의 update affected rows == 1 인지 확인. 0이면 이미 revoked → 재사용 감지로 분기 → `revokeAllByUserId`. | optimistic + affected-row check |

위 어떤 케이스도 DB 트리거를 사용하지 않는다 (D-003).

테스트로 검증할 항목 (P-002 검사방법 구체화):
- 동시 signup → 1건만 성공.
- 동시 verify (잘못된 코드 5회) → attempt_count == 5, 6번째는 INVALID_EMAIL_CODE.
- 동일 refresh로 rotate 2회 동시 호출 → 한 쪽만 새 토큰 받고, 다른 쪽은 INVALID_TOKEN + 이후 모든 refresh 폐기됨.

---

## 11. Trade-off Analysis (왜 이렇게 결정했는지)

D-001 ~ D-005는 결과만 적혀 있어 후속 리뷰 때 매번 다시 까보게 됨. 본 섹션에서 각 결정의 옵션·근거·포기한 것을 한 자리에 모은다.

### 11.1 인증 방식: JWT (D-001)
- **옵션 A** 서버 세션 (HttpSession + JSESSIONID): 즉시 만료/폐기 가능, 구현 단순. 단점: 서버 scale-out 시 세션 store 동기화(스티키 세션 / Redis 세션 store) 필요.
- **옵션 B (선택)** JWT Access + Refresh: 서버 stateless, 학교 프로젝트 인프라 단순화에 유리. React SPA의 `Authorization` 헤더 패턴과 정합. 단점: Access의 즉시 폐기가 불가능 → Access TTL을 짧게(30분) 잡아 노출 시간 한계 설정.

### 11.2 Refresh 저장소: MySQL (D-002)
- **옵션 A** Redis: 만료 자동 처리, 빠른 hit. 단점: 운영 컴포넌트 추가, 학교 프로젝트 인프라 비용/관리 부담.
- **옵션 B (선택)** MySQL `refresh_token` 테이블: 운영 단순화. 단점: 매 refresh마다 DB hit. 학교 프로젝트 RPS 규모상 무시 가능. 부수 효과 — 회전/재사용 감지 / 일괄 폐기를 SQL 한 줄로 깔끔하게 구현 가능.

### 11.3 Refresh 토큰 형태: JWT + DB token_hash 매칭
- **옵션 A** 순수 stateless JWT (DB 미저장): logout 즉시성 X, reuse 감지 X.
- **옵션 B** Opaque random token + DB 저장 (JWT 아님): 단순. 단점: 만료/서명 검증을 모두 DB가 짊어짐.
- **옵션 C (선택)** JWT(서명/만료) + DB(저장된 hash로 활성/폐기 추적): 만료·서명 검증은 stateless JWT가, 폐기·재사용 감지는 DB가 분담. 두 책임이 깔끔히 분리됨. 단점: refresh 한 번 발급할 때 두 곳에 쓴다는 복잡도.

### 11.4 BCrypt strength = 12
- 10/12/14 선택지. OWASP 권장은 환경별 1초 이내 hash 가능한 가장 큰 값. 12는 대부분 환경에서 1초 미만. 학교 프로젝트의 로그인 RPS에서는 충분히 감내 가능. 14는 로그인 latency가 가시적으로 느려질 수 있어 회피.

### 11.5 동시성 락 전략: 비관적 vs 낙관적 혼용
- **email_verification verify / consume → 비관적 락.** 둘 다 짧은 critical section + 동시 호출이 정상 흐름이 아니라 비관적 락이 단순. retry 로직 회피.
- **refresh rotation → affected-row check (낙관적 패턴).** 동시 호출 자체가 reuse 시그널이라 "충돌을 회피"가 아니라 "충돌을 감지"가 목적. 비관적 락을 잡으면 두 번째 호출이 첫 번째를 기다리느라 reuse 감지 타이밍이 흐려짐. update returning affected==0 으로 race 감지가 깔끔.

### 11.6 Logout: 전 디바이스 vs 현재 세션 → D-007 단일 세션
- **옵션 A** 전 디바이스 로그아웃 (userId 기준 모든 활성 refresh 일괄 폐기): 명세 시트의 본문 없는 형태와 정합. 보안 측면 강함. 단점: 디바이스/탭별 독립 로그아웃 안 됨.
- **옵션 B (선택, D-007)** 단일 세션 로그아웃: 본문에 refreshToken 1건 받아 해당 row 만 폐기. 디바이스/탭별 독립 UX 제공. 명세 시트 Request Body 갱신 필요 (외부 적용 대기).
- 재사용 감지로 인한 일괄 폐기 경로는 그대로 살아있음 (`rotate` 내부). Logout 만 단일 세션으로 좁힘.

### 11.7 Scale & Reliability
- 캐시·큐·HA·로드 분산은 본 스프린트 스코프 아님. 학교 프로젝트 트래픽 규모(예상 동시 사용자 수십)에 비해 오버엔지니어링. Sprint 후반/실배포 시점에 다시 본다.

---

## 12. P-006 / P-007 / P-008 처리 결과 (2026-05-12)

| 항목 | 결과 |
|---|---|
| **P-006** | 수정안 확정. ErrorCode `EMAIL_NOT_VERIFIED` (400) 본 문서 §5.2 / §9 에 이미 반영. API 명세 시트 적용은 marcus 외부 진행 (decisions-log P-006 참조). |
| **P-007** | **D-007 — 단일 세션 로그아웃 채택.** 본 문서 §3.5 / §4 / §5.1 / §5.3 / §11.6 갱신. API 명세 시트 Request Body 갱신 필요 (외부 적용 대기). |
| **P-008** | **D-008 — 가정값 (6자리/10분/5회/60초) 그대로 채택.** 본 문서 §2.3 갱신. SRS RE-SF1-01 명문화는 P-003 SRS 수정안에 포함. |

테스트 작성 단계 진입 전 차단 요소 모두 닫힘.

---

## 13. 다음 단계로 넘기는 입력 체크리스트

본 문서로 테스트 작성에 필요한 다음이 모두 픽싱됐는지:

- [x] 컨트롤러 라우트 + 메서드 시그니처 + 응답 DTO → MockMvc 통합 테스트 작성 가능
- [x] 서비스 public 메서드 시그니처 + 던지는 예외 → 단위 테스트 작성 가능
- [x] DTO 필드 + Bean Validation 어노테이션 → 입력 유효성 테스트 작성 가능
- [x] ErrorCode enum (HTTP status + code 문자열) → 에러 응답 jsonPath 비교 가능
- [x] Repository 쿼리 메서드 시그니처 → @DataJpaTest 작성 가능
- [x] §3 데이터 플로우 4종 → 통합 테스트 시나리오로 1:1 매핑 가능
- [x] §10 동시성 시나리오 3건 → 동시성 통합 테스트 작성 가능
- [ ] P-006 / P-007 / P-008 결정 (테스트 작성 전에 닫아야 함)
