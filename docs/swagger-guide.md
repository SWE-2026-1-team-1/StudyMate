# StudyMate API — Swagger 사용 가이드

## 접속 URL

| 환경 | URL |
|---|---|
| 로컬 | http://localhost:8080/swagger-ui/index.html |
| 프로덕션 | http://{EC2_IP}:8080/swagger-ui/index.html |

---

## JWT 인증 설정

인증이 필요한 API(`/api/auth/logout` 등)를 호출하려면 먼저 토큰을 등록해야 한다.

1. `POST /api/auth/login` 호출 → 응답에서 `accessToken` 값 복사
2. 우측 상단 **Authorize** 버튼 클릭
3. `bearerAuth` 항목에 복사한 토큰 붙여넣기 (`Bearer ` 접두사 없이 토큰 값만)
4. **Authorize → Close**

이후 모든 요청에 `Authorization: Bearer {token}` 헤더가 자동으로 붙는다.

---

## 테스트 흐름

### API 의존성 순서

```
send-code → verify → signup → login → [Authorize] → logout / token/refresh
```

### 로컬 시드 데이터

앱 기동 시 아래 데이터가 자동 삽입된다 (`@Profile("local")` 멱등 실행).

| 용도 | 이메일 | 비밀번호 | 비고 |
|---|---|---|---|
| login 바로 테스트 | `test@university.ac.kr` | `Test1234` | 가입 완료 상태 |
| signup 테스트 | `new@university.ac.kr` | — | `email_verification` pre-verified (코드: `000000`) |

---

### Step 1 — signup (`new@university.ac.kr`)

`send-code` / `verify` 단계는 시드 데이터가 대체하므로 생략.

```json
POST /api/auth/signup
{
  "email": "new@university.ac.kr",
  "password": "Test1234",
  "name": "신규유저"
}
```

### Step 2 — login → accessToken 획득

```json
POST /api/auth/login
{
  "email": "test@university.ac.kr",
  "password": "Test1234"
}
```

응답:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "userId": 1
}
```

### Step 3 — Authorize 등록

위 `accessToken` 값을 복사해 **Authorize** 버튼에 붙여넣기.

### Step 4 — logout

```json
POST /api/auth/logout
{
  "refreshToken": "eyJ..."
}
```

### Step 5 — token/refresh

```json
POST /api/auth/token/refresh
{
  "refreshToken": "eyJ..."
}
```

---

## 주의사항

- **`send-code`는 실제 SMTP 필요.** 로컬에서 호출하면 `500` 반환 — 의도된 동작이므로 무시.
- **토큰 만료**: Access Token TTL 30분. 만료 시 `token/refresh`로 재발급 후 Authorize에 새 토큰 등록.
- **signup 재시도**: `new@university.ac.kr`로 한 번 가입하면 `email_verification` 레코드가 소비된다. 재테스트 시 앱을 재시작하면 시드가 다시 삽입된다.
