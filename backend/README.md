# StudyMate Backend

StudyMate의 Spring Boot 3.4 / Java 21 백엔드.

작업 시작 전에 레포 루트의 [`CONTRIBUTING.md`](../CONTRIBUTING.md) 를 먼저 읽을 것. 언어 정책(한국어 기본), 브랜치·커밋 컨벤션, 코드 주석 룰, TDD 정책, AI 도구 사용 시 유의사항 등이 정리되어 있다.

---

## 스택

- **언어 / 런타임**: Java 21 (Gradle toolchain이 핀 — 로컬에 21이 없어도 Gradle이 받아옴)
- **프레임워크**: Spring Boot 3.4.5 (Web, Security, Data JPA, Validation, Mail, Actuator)
- **DB**: MySQL 8.x (prod / local), H2 (테스트 전용)
- **인증**: JWT (`io.jsonwebtoken:jjwt 0.12.6`)
- **API 문서**: springdoc-openapi 2.8.4 (Swagger UI)
- **빌드**: Gradle wrapper (`./gradlew`)

---

## 사전 준비

1. **Java 21** — Gradle toolchain이 자동으로 받아오지만, `sdkman`이나 Homebrew로 로컬에 깔아두면 트러블이 적다.
2. **MySQL 8.x** — `localhost:3306` 에 띄울 것.
   - `application-local.yml` 의 기본 자격증명: user `root`, password `1234`.
   - 본인 환경이 다르면 로컬에서만 수정하고 커밋하지 말 것.
3. **DB 스키마** — 빈 스키마 생성:
   ```sql
   CREATE DATABASE studymate
     DEFAULT CHARACTER SET utf8mb4
     DEFAULT COLLATE utf8mb4_unicode_ci;
   ```
   레포 루트에서 스키마 적용:
   ```bash
   mysql -u root -p studymate < ../studymate_schema.sql
   ```
   > SQL 파일의 `CREATE TRIGGER` 구문은 의도적으로 **사용하지 않는다** (`CONTRIBUTING.md` §6 참조). DB에는 만들어지지만 애플리케이션 로직은 무시하고, 동등한 규칙은 서비스 레이어에서 강제한다.

---

## 로컬 실행

`backend/` 디렉터리에서:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

`local` 프로필은 `application-local.yml` 을 사용하며:

- `jdbc:mysql://localhost:3306/studymate` 에 `root` / `1234` 로 접속.
- 메일 헬스체크 비활성화 (SMTP 자격증명 없어도 부팅).
- Swagger UI: `http://localhost:8080/swagger-ui.html`.
- 개발 전용 JWT 시크릿 사용. **이 시크릿은 운영에 절대 쓰지 말 것.**

메일을 로컬에서 실제로 동작시키려면 실행 전에 `MAIL_USERNAME`, `MAIL_PASSWORD` 환경 변수를 설정.

---

## 빌드 / 테스트

```bash
./gradlew build         # 테스트 포함 풀 빌드
./gradlew test          # 테스트만
./gradlew bootJar       # build/libs/ 에 실행 가능한 jar 생성
```

테스트는 H2 인메모리 — 로컬 MySQL을 건드리지 않는다.

---

## 프로필별 설정

| 프로필   | 파일                         | 용도                                          |
|----------|------------------------------|-----------------------------------------------|
| default  | `application.yml`            | 공통 기본값, 환경 변수 참조                   |
| `local`  | `application-local.yml`      | 로컬 개발 (localhost MySQL)                   |
| `prod`   | `application-prod.yml`       | 운영 (모든 시크릿은 환경 변수)                |

### 운영 환경에 필요한 환경 변수

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` (≥ 32자)
- `MAIL_USERNAME`, `MAIL_PASSWORD`

실제 값은 **무엇이든 커밋하지 말 것**. `application.yml` 의 기본값은 개발용 플레이스홀더일 뿐이다.

---

## API 문서

`local` 프로필로 띄운 상태에서:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Swagger는 코드에서 자동 생성되며 API 표면의 source of truth (`CONTRIBUTING.md` §7 참조).

---

## 헬스체크

- `GET /actuator/health` — 인증 예외. 로드밸런서 헬스체크용. 인증으로 막지 말 것.

---

## 자주 걸리는 함정

- **`ddl-auto: validate`** — JPA 엔티티와 실제 스키마가 다르면 부팅이 실패한다. 검증 에러로 안 뜨면 엔티티를 고치거나 마이그레이션을 적용할 것. `update` 로 바꿔서 덮는 식의 회피는 금지.
- **JWT 시크릿 길이** — `jjwt 0.12` 는 최소 256비트(≈ ASCII 32자) 필요. 짧으면 부팅 실패.
- **MySQL 타임존** — JDBC URL이 `serverTimezone=UTC` 로 고정. MySQL 서버가 다른 타임존이면 timestamp가 어긋남. 서버를 UTC로 맞추거나 URL을 의도적으로 변경.
- **DB 트리거 미사용** — `CONTRIBUTING.md` §6 참조. 비즈니스 invariant는 `@Transactional` 서비스 코드에서 적절한 락으로 처리.
