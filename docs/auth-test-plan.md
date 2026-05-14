# Sprint 2 — 인증 도메인 테스트 플랜

> **목적.** 다음 단계(테스트 코드 작성)의 입력. `docs/auth-class-design.md` 의 외부 시그니처 + SRS 검사기준 + D-001~D-009 결정을 테스트 케이스 ID 단위로 깎아둔다.
>
> **테스트 코드를 미리 쓰지 않는다.** 본 문서가 픽싱하는 것은 (1) 어느 컴포넌트에 어느 타입의 테스트를 어디까지 쓸지, (2) 각 시나리오에서 검증할 행위·상태 어서션, (3) SRS 검사기준이 어떤 테스트로 닫히는지 매핑. 단계의 산출물 형태는 "체크박스 목록"이다.
>
> **Reader.** 1차: 다음 세션의 채범수(테스트 코드 작성). 2차: 검사기준/요구사항 추적표 갱신 시점(Sprint 2 종료, P-005)의 본인 또는 팀.
>
> **Scope 외 명시.** 부하 테스트(JMeter/k6) · E2E(Cypress 등) · UI 테스트 · 보안 침투 · **동시성 회귀 테스트(D-009)**. 본 스프린트 검증 목적은 *명세대로 동작하는지* 뿐.

---

## 목차

1. 입력 체크리스트
2. 테스트 피라미드 매핑 + 컴포넌트별 전략
3. 도구·픽스처·테스트 더블 결정
4. 시나리오별 테스트 케이스 (auth-class-design §3 1:1)
   - 4.1 이메일 코드 발송 (`/email/send-code`)
   - 4.2 이메일 코드 검증 (`/email/verify`)
   - 4.3 회원가입 (`/signup`)
   - 4.4 로그인 (`/login`)
   - 4.5 토큰 재발급 (`/token/refresh`) — Rotation + Reuse
   - 4.6 로그아웃 (`/logout`) — D-007 단일 세션
5. 입력 유효성 테스트 매트릭스 (RE-NF-03)
6. SRS 검사기준 ↔ 테스트 매핑 표
7. 커버 / 스킵 정책
8. 커버리지 목표
9. 다음 단계 진입 체크리스트

---

## 1. 입력 체크리스트

테스트 코드 작성을 시작하기 전에 다음이 모두 픽싱돼 있어야 한다.

- [x] `docs/auth-class-design.md` §2 클래스 책임 + §3 데이터 플로우 + §4 컨트롤러 시그니처 + §5 서비스 시그니처 + §6 Repository 시그니처 + §7 Entity 필드 + §8 DTO + §9 ErrorCode + §10 동시성 정책 (코드 레벨 방어 — 회귀 테스트는 D-009 로 제외)
- [x] `docs/decisions-log.md` D-001 ~ D-009 모두 closed
- [x] SRS RE-SF1-01 / RE-SF1-02 / RE-NF-01 / RE-NF-03 검사기준
- [x] `studymate_schema.sql` 의 `app_user` / `email_verification` / `refresh_token` 컬럼 (D-006)
- [x] D-008 정책 상수 — 코드 6자리 / TTL 10분 / 시도 5회 / 쿨다운 60초

---

## 2. 테스트 피라미드 매핑 + 컴포넌트별 전략

```
            /  통합 (얇게)         \  @SpringBootTest + MockMvc — HTTP 계약
           /  슬라이스 (적당히)      \  @WebMvcTest, @DataJpaTest
          /  단위 (두텁게)            \  Service / 도메인 로직 / DTO Validation
```

| 컴포넌트 | 테스트 타입 | 이유 |
|---|---|---|
| `AuthController` | `@WebMvcTest` 슬라이스 + `MockMvc`, Service mock | Request 매핑 / `@Valid` 동작 / HTTP status / JSON 직렬화만 확인. 비즈니스 로직 X. |
| `AuthService` / `EmailVerificationService` / `TokenService` | 순수 Mockito 단위 | 분기·예외·호출 순서 검증. DB / 시간 / 메일은 mock + `Clock.fixed`. |
| `JwtTokenProvider` | 순수 단위 (Mockito 없이) | 생성 → 파싱 round-trip, 만료/서명/잘못된 포맷 처리. |
| `JwtAuthenticationFilter` | `@WebMvcTest` 일부 + 토큰 빌더 헬퍼 | 인증된 사용자 컨텍스트 주입 / 인증 실패 시 401 통과. |
| Repository | `@DataJpaTest` (H2) | `findUsableForSignup` / `findByTokenHash` / `revokeAllByUserId` 쿼리 동작 확인. |
| Entity 도메인 메서드 (`EmailVerification.markVerified` 등) | 순수 단위 | 가드 조건(이미 verified면 변경 X 등). |
| 인증 전체 시나리오 6종 | `@SpringBootTest` + MockMvc + H2 | 컨트롤러부터 DB까지 통합. SRS 검사기준 닫는 위치. |

**검증 종류 구분.**
- *동작 어서션*: 응답 status / body JSON / `verify` 시 `INVALID_EMAIL_CODE` 같은 ErrorCode.
- *상태 어서션*: DB row의 `attemptCount` / `consumedAt` / `revokedAt` 같은 필드.
- *상호작용 어서션*: `emailSender.send(email, code)` 호출 여부 / 호출 횟수.

세 종류를 매 케이스에 명시한다 (§4 표의 "검증 포인트" 칸).

---

## 3. 도구·픽스처·테스트 더블 결정

| 항목 | 결정 | 비고 |
|---|---|---|
| 통합/슬라이스 테스트 DB | **H2** (MySQL 호환 모드, `MODE=MYSQL`) | D-009 로 동시성 회귀 테스트 제외 → 락 시멘틱 차이 영향 없음. JPQL / `@Modifying` / `@Lock(PESSIMISTIC_WRITE)` 의 인증 도메인 사용은 H2 에서 그대로 동작. Docker 의존 회피. |
| 시간 | `java.time.Clock` 빈 주입. 테스트는 `Clock.fixed(Instant, ZoneOffset.UTC)` 또는 mutable `MutableClock` 헬퍼. | `EmailVerificationService.sendCode` 쿨다운 / `TTL` 검증에 필수. |
| 이메일 | `EmailSender` 인터페이스. 테스트 더블 `FakeEmailSender` — 마지막 발송 code/email 보관, 카운터. | Mockito mock 도 OK 지만 통합 시나리오에서는 Fake 가 어서션이 단순. |
| 비밀번호 인코더 | 통합 테스트도 `BCryptPasswordEncoder(strength=12)` 그대로. | 의도적 부하 회피하려고 strength 낮추지 않음 (verify 한두 번이라 무시 가능). |
| JWT | `JwtTokenProvider` 실제 빈 사용. 테스트용 짧은 만료(예: Access 100ms)는 별도 `TestTokenProvider` 또는 `@TestConfiguration` 로 override. | 만료 케이스 어서션용. |
| 통합 테스트 트랜잭션 | `@Transactional` **사용 OK** — 자동 롤백으로 케이스 간 격리. 단, 컨트롤러가 자체 트랜잭션을 커밋한 결과를 outer test transaction 의 영속성 컨텍스트가 stale 한 entity 로 캐시할 수 있으니, **DB 상태 어서션 직전에 `entityManager.clear()` 또는 `JdbcTemplate` 으로 직접 조회**. `@DataJpaTest` 슬라이스도 기본 롤백 그대로. | 동시성 테스트가 빠지면서(D-009) `@Transactional` 회피 사유 소멸. |
| 테스트 데이터 빌더 | `UserFixture`, `RefreshTokenFixture` static 메서드. JPA save 후 id 반환. | builder 라이브러리 없이 정적 메서드로 충분. |

---

## 4. 시나리오별 테스트 케이스 (auth-class-design §3 1:1)

각 표의 "ID" 는 테스트 메서드 명에 그대로 박는다. 형식: `T-<엔드포인트약자>-<번호>`.
"레이어" 는 위에서 정한 컴포넌트별 전략에 맞춘다 (`U` = 단위, `WMVC` = `@WebMvcTest`, `DJ` = `@DataJpaTest`, `SBT` = `@SpringBootTest` 통합).

### 4.1 이메일 코드 발송 (`POST /api/auth/email/send-code`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-SEND-01 | SBT | 신규 이메일 `a@university.ac.kr` | 200, `email_verification` row 1건 생성 (`codeHash` 길이 64, `expiresAt = now + 10m`, `verifiedAt = null`, `consumedAt = null`, `attemptCount = 0`), `FakeEmailSender` 가 동일 이메일/6자리 숫자 코드 1회 수신 | 동작/상태/상호작용 |
| T-SEND-02 | WMVC | 비-이메일 형식 `not-an-email` | 400 `INVALID_INPUT` (Bean Validation @Email 위반) | 동작 |
| T-SEND-03 | WMVC | 학교 도메인 외 `a@gmail.com` | 400 `INVALID_INPUT` (`@Pattern`) | 동작 |
| T-SEND-04 | WMVC | `email` 누락 | 400 `INVALID_INPUT` (`@NotBlank`) | 동작 |
| T-SEND-05 | SBT | 사전: `app_user` 에 동일 이메일 가입 존재 (`isDeleted=false`) | 409 `CONFLICT`, `email_verification` row 미생성, 메일 발송 0회 | 동작/상태/상호작용 |
| T-SEND-06 | SBT | 사전: 같은 이메일 send-code 30초 전 호출 (DB createdAt 픽스) | 400 `INVALID_INPUT` (메시지 "잠시 후 다시 시도해주세요"), 신규 row 미생성, 메일 미발송 | 동작/상태/상호작용 |
| T-SEND-07 | SBT | 사전: 같은 이메일 send-code 61초 전 호출 | 200, 신규 row 1건 추가 (총 2건), 새 코드 메일 발송 | 상태/상호작용 |
| T-SEND-08 | U (Service) | `EmailVerificationService.sendCode` → repository / sender 모킹. 충돌 None. | `SecureRandom` 6자리 코드 생성, `SHA-256` 해시 row 저장, `emailSender.send(email, plainCode)` 호출 | 동작/상호작용 |

### 4.2 이메일 코드 검증 (`POST /api/auth/email/verify`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-VRFY-01 | SBT | 사전: T-SEND-01 직후 row + 정확한 코드 | 200, 해당 row `verifiedAt != null`, `attemptCount = 0` | 동작/상태 |
| T-VRFY-02 | SBT | 사전: row 있음, 잘못된 코드 | 400 `INVALID_EMAIL_CODE`, `attemptCount = 1`, `verifiedAt = null` | 동작/상태 |
| T-VRFY-03 | SBT | 잘못된 코드 5회 → 정확한 코드 6번째 | 1~4회: 400 `INVALID_EMAIL_CODE`, `attemptCount` 증가. 5회째: `attemptCount=5` 도달. 6번째 정답 입력: 400 `INVALID_EMAIL_CODE` (시도 횟수 초과로 거절), `verifiedAt = null` | 동작/상태 |
| T-VRFY-04 | SBT | 사전: 만료된 row (`expiresAt < now`) + 정확한 코드 | 400 `EXPIRED_EMAIL_CODE`, `verifiedAt = null` | 동작/상태 |
| T-VRFY-05 | SBT | 사전: 해당 이메일에 미소비 row 없음 | 400 `INVALID_EMAIL_CODE` | 동작 |
| T-VRFY-06 | SBT | 사전: 같은 이메일에 row 두 건 (오래된 + 새로) | 최신 row 만 매칭됨 (`findTopBy...OrderByCreatedAtDesc` 동작 확인) | 상태 |
| T-VRFY-07 | WMVC | `code` 가 5자리 / 7자리 | 400 `INVALID_INPUT` (`@Size(min=6,max=6)`) | 동작 |
| T-VRFY-08 | WMVC | `email`/`code` 누락 | 400 `INVALID_INPUT` (`@NotBlank`) | 동작 |
| T-VRFY-09 | DJ | `EmailVerificationRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc` 쿼리 | consumed != null row 는 제외, 가장 최신 미소비 row 반환 | 동작 |

### 4.3 회원가입 (`POST /api/auth/signup`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-SIGN-01 | SBT | 사전: `email_verification` 에 `verified, 미소비, 미만료` row. 신규 이메일. | 201 `{userId, email, name}`, `app_user` 1건 (`passwordHash` ≠ plain, `isEmailVerified=true`, `isDeleted=false`), 해당 EV row `consumedAt != null` | 동작/상태 |
| T-SIGN-02 | SBT | 사전: EV row 자체 없음 | 400 `EMAIL_NOT_VERIFIED`, `app_user` 미생성 | 동작/상태 |
| T-SIGN-03 | SBT | 사전: EV row 있으나 `verifiedAt = null` | 400 `EMAIL_NOT_VERIFIED` | 동작 |
| T-SIGN-04 | SBT | 사전: EV row 있으나 이미 `consumedAt != null` | 400 `EMAIL_NOT_VERIFIED` (재사용 차단) | 동작 |
| T-SIGN-05 | SBT | 사전: EV row 있으나 `expiresAt <= now` (verified 후 시간 초과) | 400 `EMAIL_NOT_VERIFIED` | 동작 |
| T-SIGN-06 | SBT | 사전: EV row OK + `app_user` 동일 이메일 존재 | 409 `CONFLICT`, EV row `consumedAt != null` (소비됨; 재시도 시 새 인증 필요한 의도 그대로) | 동작/상태 |
| T-SIGN-07 | WMVC | password = `abc123` (7자, 영문+숫자 OK) | 400 `INVALID_INPUT` | 동작 |
| T-SIGN-08 | WMVC | password = `abcdefgh` (8자, 숫자 없음) | 400 `INVALID_INPUT` | 동작 |
| T-SIGN-09 | WMVC | password = `12345678` (8자, 영문 없음) | 400 `INVALID_INPUT` | 동작 |
| T-SIGN-10 | WMVC | password = `abc12345` (정상 경계, 8자) | Validation 통과 (Service 단까지 진입; Service 는 mock) | 동작 |
| T-SIGN-11 | WMVC | password = 21자 영문+숫자 | 400 `INVALID_INPUT` | 동작 |
| T-SIGN-12 | WMVC | password = 영문+숫자+특수문자 `abc12345!` | 400 `INVALID_INPUT` (현재 정규식은 영문+숫자만 허용; D 결정으로 명문화돼 있지 않으면 §11에 사용 결정 박을지 별도 결정 필요) — **테스트 자체는 현 정규식 기준** | 동작 |
| T-SIGN-13 | U (Service) | `signup` 호출, EV row mock 으로 usable 응답 → `userRepository.save` 호출 직전에 `consumeVerifiedRecord` 가 먼저 호출되는 순서 검증 | Mockito `InOrder` 로 호출 순서 확인 (consume → existsBy → save) | 상호작용 |

### 4.4 로그인 (`POST /api/auth/login`)

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-LOGIN-01 | SBT | 사전: T-SIGN-01 결과 user. 정확한 비밀번호. | 200 `{accessToken, refreshToken, userId}`, `refresh_token` 1건 신규(`tokenHash = sha256(refreshToken)`, `revokedAt = null`, `expiresAt ≈ now+14d`) | 동작/상태 |
| T-LOGIN-02 | SBT | 사전: 미존재 이메일 | 401 `INVALID_CREDENTIALS`, `refresh_token` 미생성 | 동작/상태 |
| T-LOGIN-03 | SBT | 사전: 존재 user. 잘못된 비밀번호. | 401 `INVALID_CREDENTIALS`, `refresh_token` 미생성, 메시지가 T-LOGIN-02 와 동일 (사용자 존재 여부 누설 방지) | 동작 |
| T-LOGIN-04 | SBT | 사전: `isDeleted=true` user | 401 `INVALID_CREDENTIALS` (soft delete 된 계정 차단) | 동작 |
| T-LOGIN-05 | WMVC | `email`/`password` 누락 | 400 `INVALID_INPUT` | 동작 |
| T-LOGIN-06 | U (Service) | `passwordEncoder.matches` 호출되는지, `JwtTokenProvider.createAccessToken` / `createRefreshToken` 호출 → `RefreshTokenRepository.save` 호출 순서 | Mockito `InOrder` | 상호작용 |
| T-LOGIN-07 | U (JwtTokenProvider) | `createAccessToken(userId, email)` → `parse(token).getSubject() == userId` 등 round-trip | 동작 |

### 4.5 토큰 재발급 (`POST /api/auth/token/refresh`) — Rotation + Reuse

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-RFRSH-01 | SBT | 사전: T-LOGIN-01 발급 refresh. 1회 호출. | 200 `{accessToken, refreshToken}`, **신규** refresh 토큰 (이전과 다름), 이전 row `revokedAt != null`, 신규 row 1건 추가 | 동작/상태 |
| T-RFRSH-02 | SBT | T-RFRSH-01 직후, 이미 회전된 *이전* refresh 로 재시도 (재사용 시그널) | 401 `INVALID_TOKEN`, 해당 user 의 모든 활성 refresh 일괄 `revokedAt != null` (T-RFRSH-01 결과로 발급된 신규 refresh 까지 폐기) | 동작/상태 |
| T-RFRSH-03 | SBT | 만료된 refresh 입력 | 401 `TOKEN_EXPIRED`, DB 변화 없음 | 동작/상태 |
| T-RFRSH-04 | SBT | DB 에 없는 (서명만 valid) refresh 입력 | 401 `INVALID_TOKEN` | 동작 |
| T-RFRSH-05 | SBT | 서명 불일치 / 변조된 refresh 입력 | 401 `INVALID_TOKEN` | 동작 |
| T-RFRSH-06 | WMVC | `refreshToken` 누락 | 400 `INVALID_INPUT` | 동작 |
| T-RFRSH-07 | U (Service) | `tokenService.rotate` 호출 시 `revokeById` 의 affected 0 반환 → `revokeAllByUserId` 호출 후 `INVALID_TOKEN` 던짐 | Mockito | 동작/상호작용 |
| T-RFRSH-08 | DJ | `RefreshTokenRepository.revokeById` modifying 쿼리: 이미 revoked row 호출 시 affected = 0 | 동작 |
| T-RFRSH-09 | DJ | `revokeAllByUserId` 가 해당 user 의 활성 row 만 `revokedAt` set | 상태 |

T-RFRSH-02 는 순차 재사용(이전 refresh 를 일부러 다시 보냄)으로 검증 — 동시 호출이 아니므로 D-009 와 무관하게 본 스코프 유지.

### 4.6 로그아웃 (`POST /api/auth/logout`) — D-007 단일 세션

| ID | 레이어 | 입력 / 사전 상태 | 기대 동작 | 검증 포인트 |
|---|---|---|---|---|
| T-LOGOUT-01 | SBT | 사전: T-LOGIN-01 결과 user 가 2개 디바이스 로그인 (refresh A, refresh B). Authorization=Bearer A's access, Body=`{refreshToken: A}` | 200, A row `revokedAt != null`, **B row `revokedAt = null`** (단일 세션) | 동작/상태 |
| T-LOGOUT-02 | SBT | 사전: A 발급 user. 인증 헤더 없음. | 401 `UNAUTHORIZED` (Spring Security 진입 자체 차단), DB 변화 없음 | 동작/상태 |
| T-LOGOUT-03 | SBT | 사전: A 발급 user1 + 다른 user2. Authorization=user1 access, Body=`{refreshToken: user2's refresh}` | 401 `INVALID_TOKEN` (소유권 검증 실패), user2 row 변화 없음 | 동작/상태 |
| T-LOGOUT-04 | SBT | 만료된 refresh 본문 입력 | 401 `TOKEN_EXPIRED` | 동작 |
| T-LOGOUT-05 | SBT | DB 에 없는 refresh 본문 입력 | 401 `INVALID_TOKEN` | 동작 |
| T-LOGOUT-06 | SBT | 이미 `revokedAt` set 된 refresh 입력 | 200 (idempotent OK; `revokedAt` 변화 없음) — auth-class-design §3.5 마지막 줄 결정 | 동작/상태 |
| T-LOGOUT-07 | WMVC | `refreshToken` 본문 누락 | 400 `INVALID_INPUT` | 동작 |
| T-LOGOUT-08 | U (Service) | `tokenService.revokeOne` 호출 시 `row.userId != principal.userId` 분기 | `AuthException(INVALID_TOKEN)` | 동작 |

---

## 5. 입력 유효성 테스트 매트릭스 (RE-NF-03)

`@Valid` + Bean Validation 메시지가 `INVALID_INPUT` 으로 변환되는 경로. `@WebMvcTest` + `MockMvc` 슬라이스에서 한 번에 깎는다.

| Request DTO | 필드 | 케이스 | 기대 |
|---|---|---|---|
| `SignupRequest` | email | null / 빈 문자열 / `not-an-email` / `a@gmail.com` (학교 도메인 X — but signup 은 학교 도메인 강제 안 함 §8.1 기준; send-code 만 강제) | null/빈/포맷오류만 400. gmail 도메인은 통과 → 다만 EV 없어 EMAIL_NOT_VERIFIED 단계로. |
| `SignupRequest` | password | §4.3 T-SIGN-07 ~ T-SIGN-12 참조 | |
| `SignupRequest` | name | null / 빈 / 101자 | 400 `INVALID_INPUT` |
| `LoginRequest` | email | null / 빈 / 포맷오류 | 400 |
| `LoginRequest` | password | null / 빈 | 400 |
| `SendEmailCodeRequest` | email | null / 빈 / `not-an-email` / `a@gmail.com` (학교 도메인 위반) | 모두 400 |
| `VerifyEmailCodeRequest` | email | null / 빈 / 포맷오류 | 400 |
| `VerifyEmailCodeRequest` | code | null / 빈 / 5자 / 7자 | 400 |
| `RefreshTokenRequest` | refreshToken | null / 빈 | 400 |

응답 JSON shape: `{ "code": "INVALID_INPUT", "message": "<첫 위반 메시지>" }`. ApiErrorResponse 어서션 한 번에 묶음.

---

## 6. SRS 검사기준 ↔ 테스트 매핑 표

추후 P-005(요구사항 추적표) 갱신 시 그대로 옮겨갈 수 있도록 명시.

| 검사기준 No | 요구사항 ID | 검사 요지 | 닫는 테스트 |
|---|---|---|---|
| 기능 No.1 | RE-SF1-01 | 검증된 이메일은 가입 가능, 미검증 이메일은 거절, 가입 후 로그인 성공 | T-SEND-01, T-VRFY-01, T-SIGN-01 ~ T-SIGN-05, T-LOGIN-01 |
| 기능 No.2 | RE-SF1-02 | 가입된 계정으로 로그인 가능, 로그아웃 후 인증 필요 페이지 차단 | T-LOGIN-01, T-LOGOUT-01, T-LOGOUT-02 (헤더 없이 인증 필요 호출 시 401) |
| 비기능 No.1 | RE-NF-01 | DB 비밀번호 해시 저장, 만료 토큰으로 호출 시 401 | T-SIGN-01 상태 어서션 (passwordHash != plain, BCrypt 패턴 시작), T-RFRSH-03 / T-LOGOUT-04 |
| 비기능 No.3 | RE-NF-03 | 입력 형식 위반 시 오류 메시지 | §5 매트릭스 전부 |

비고: RE-NF-06 (동시성 무결성) 은 D-009 로 본 스프린트 회귀 테스트 스코프 외. 코드 레벨 방어(D-003 트랜잭션+락, rotate affected-row 분기)는 그대로 유지되며, SRS 검사기준 본문은 변경하지 않는다. 스터디/지원 도메인의 동시성 시나리오는 본 스프린트 범위 외 (해당 도메인 구현 시점에 재검토).

---

## 7. 커버 / 스킵 정책

**커버 (testing-strategy "Focus on" 항목 적용)**
- 비즈니스 핵심 경로: signup → login → refresh → logout 의 정상 흐름과 실패 분기.
- 에러 핸들링: 9 ErrorCode 모두 적어도 1개 테스트가 검증.
- 엣지 케이스: 만료 직전/직후, attempt 5회 경계, 순차 재사용 시그널.
- 보안 경계: 비밀번호 해시 / 토큰 소유권 / 재사용 감지 / 다른 사용자 refresh 사용.
- 데이터 무결성(단일 스레드 범위): `consumedAt` 단조성, `revokedAt` idempotency, refresh hash 유일성.

**스킵 (testing-strategy "Skip" 항목 적용)**
- DTO record 의 자동 생성 getter / record canonical equals.
- Spring Security 프레임워크 내부.
- `JwtTokenProvider` 의 라이브러리 위임 메서드 (parse / claims 매핑) 의 라이브러리 자체 동작.
- `GlobalExceptionHandler` 의 매핑 자체는 별도 단위 테스트 1~2개만 깎고, 각 시나리오에서 매번 어서션 안 함 (status + code 만 보면 됨).
- 부하 / 응답 시간 (RE-NF-04 는 본 스프린트 범위 외).
- **동시성 회귀 통합 테스트 — D-009 결정으로 본 스프린트 범위 외.** 코드 레벨 방어(트랜잭션+락, affected-row 분기) 자체는 유지되며, 운영 단계 진입 시 재도입 검토.

---

## 8. 커버리지 목표

수치는 절대값보다 *어디가 비었는가*가 중요. 그래도 가이드라인:

- `auth.service` 패키지 line coverage **90% 이상**, branch coverage **80% 이상**.
- `auth.controller` 패키지: 시그니처별 정상/실패 1개씩 닫혔으면 충분.
- `auth.domain` (Entity 도메인 메서드): branch coverage **100%** (가드 조건 모두).
- `auth.security` (`JwtTokenProvider`): round-trip + 만료 + 변조 3건.
- 미달 시 PR 머지 금지 같은 강제 정책은 학교 프로젝트 규모상 박지 않음; 다만 본 문서 §4 표의 ID 가 모두 코드로 옮겨졌는지가 1차 게이트.

---

## 9. 다음 단계 진입 체크리스트

테스트 코드 작성 진입 직전 확인:

- [ ] `JwtTokenProvider` 의 라이브러리 (`jjwt` 0.12.x 권장) — 코드 작성 시 결정. 테스트 플랜에는 영향 없음.
- [ ] `application-test.yml` 분리 (테스트용 secret, JWT 만료, Mail Sender bean override, H2 datasource URL)
- [ ] FakeEmailSender 인터페이스 구현 + `@TestConfiguration` 으로 빈 교체 준비
- [ ] §4 의 모든 ID 가 테스트 메서드명에 1:1 매핑되도록 패키지 구조 결정 (예: `auth/AuthSignupIntegrationTest.java`)
- [ ] §5 매트릭스의 DTO 별 validation 테스트는 한 클래스로 묶기 (`AuthRequestValidationTest`)

이 5개를 닫으면 본 문서가 작성된 시점부터 코드 한 줄도 새로 결정할 일 없이 깎을 수 있다. 결정이 필요한 항목이 나타나면 `docs/decisions-log.md` 에 등록 후 본 문서를 업데이트한다.
