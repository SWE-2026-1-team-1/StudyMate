# StudyMate Decisions & Discrepancies Log

문서 간 모순/모호함을 해결하면서 확정된 결정과, 아직 정합화가 필요한 항목을 추적한다.
PR/이슈/SRS·SDP·API 명세·SQL 스키마·UML 개정 시 이 문서를 함께 참조한다.

## 변경 이력

| 날짜 | 작성자 | 변경 내용 |
|---|---|---|
| 2026-05-11 | 채범수 | 초안 작성 — Sprint 2 (인증) 관련 결정 D-001 ~ D-005, 미해결 P-001 ~ P-005 등록 |
| 2026-05-12 | 채범수 | 클래스 설계 단계에서 발견된 명세-결정 간극 등록 — 미해결 P-006 ~ P-008 |
| 2026-05-12 | 채범수 | 미해결 P 항목 일괄 정리. DB 작업 인계받아 `tmp(2).sql` → `studymate_schema.sql` 정비 (P-001 → D-006). Logout 정책 단일 세션 확정 (P-007 → D-007). 이메일 인증 정책 상수 확정 (P-008 → D-008). P-002 / P-003 / P-004 / P-006 수정안 확정 (외부 문서 적용 대기 상태로 전환). DB 담당 채범수로 이관. |
| 2026-05-12 | 채범수 | P-003 / P-004 / P-006 외부 문서 적용 완료 (SRS Google Docs · 작업 시트 · API 명세 시트). 모두 CLOSED 처리. 남은 미해결은 P-005 (Sprint 2 종료 후 정리) 뿐. |
| 2026-05-12 | 채범수 | 동시성 테스트 스코프 제외 결정 (D-009). 테스트 통합 DB 도 H2 로 확정. |
| 2026-05-14 | 채범수 | Sprint 3 (스터디 CRUD) 진입. 스터디 = 모집글 = 팀 스페이스 통합 모델 확정 (D-010). 모집 인원(maxMembers) 변경 룰 확정 (D-011). 스터디 생성 시 LEADER 멤버 row 동시 생성 명문화 (D-012). |
| 2026-05-20 | 채범수 | Sprint 3 설계 진입 전 SRS/API 명세 모호점 검토. `durationWeeks` / `languages` 누락 보강 (D-013), 모집 상태 OPEN ↔ CLOSED 양방향 전이 허용 (D-014), 본 스프린트 구현 디폴트 묶음 등록 (D-015). |
| 2026-05-20 | AI | 스키마 검증 실패 오류 해결을 위해 TINYINT UNSIGNED를 INT UNSIGNED로 일괄 변경 (D-016). |
| 2026-05-20 | AI | 스키마 검증 실패 오류 해결을 위해 study_member.left_reason 컬럼을 VARCHAR(20)로 변경 (D-017). |

---

## 1. 확정된 결정 (Decisions)

### D-001. 인증 방식: Spring Security + JWT (Access + Refresh)
- 범위: Sprint 2 (인증)
- 결정: `Spring Security` 필터 체인 기반, JWT Access Token + Refresh Token (Rotation 적용).
- 토큰 만료 (제안): Access 30분 / Refresh 14일.
- 근거: SRS RE-NF-01 "세션 또는 토큰 기반 인증" 중 토큰 방식 선택. React SPA 클라이언트와 정합.
- 영향 문서: SDP §3.2 (기술 스택 - 보안 항목 보강 검토).

### D-002. Refresh Token 저장소: MySQL 테이블
- 범위: Sprint 2
- 결정: Redis 도입 없이 MySQL `refresh_token` 테이블에 저장. 토큰 자체는 평문 저장 금지, `token_hash`로 저장.
- 컬럼(안): `id, user_id, token_hash, expires_at, revoked_at, created_at`.
- 근거: 인프라 단순화, 학교 프로젝트 규모에 적합.
- 후속 작업: 시서경 — SQL 스키마에 테이블 추가 (P-001 참조).

### D-003. DB 트리거 사용하지 않음 (애플리케이션 레이어로 이전)
- 범위: 전체
- 결정: 현재 `studymate_schema.sql`의 트리거(`trg_*`)와 가드 테이블(`*_guard`)이 강제하던 규칙은 제거 또는 무시한다. 동일한 규칙을 서비스 레이어의 트랜잭션 + 락(비관/낙관)으로 옮긴다.
- 근거: 비즈니스 로직이 DB와 애플리케이션에 흩어지는 것을 방지. 디버깅·테스트·이식성 측면 일관성 확보.
- 영향 항목:
  - 활성 멤버 / 활성 리더 유일성 → `study_member` 트랜잭션 + 유니크 제약(필요 시 단순화된 형태로 유지) 으로 처리
  - 정원 초과 방지 → `SELECT ... FOR UPDATE` 또는 `current_member_count`에 대한 낙관적 락
  - PENDING 신청 유일성 → `application` 트랜잭션 + 유니크 제약(`UNIQUE(study_id, applicant_id, status='PENDING')` 대안 검토)
  - SRS RE-NF-06 검사기준 절차 수정 필요 (P-002)

### D-004. 회원가입 시점에 관심 태그 저장하지 않음 (PATCH 분리)
- 범위: Sprint 2 ~ Sprint 3
- 결정: `/api/auth/signup` Request Body는 `{email, password, name}`만 받는다. 관심 태그(`interests`) / 언어(`languages`) 등록은 가입 직후 `/api/mypage/profile` PATCH로 별도 호출한다.
- 근거: API 명세를 진실로 채택. 작업 시트 #15 "프로필 관심태그 저장 (회원가입시)"의 문구와 불일치 → 시트를 명세 쪽에 맞춰 수정 (P-004).
- 클라이언트 흐름: 회원가입 완료 → 자동 로그인 또는 명시 로그인 → 프로필 등록 화면 → PATCH.

### D-005. 이메일 인증 흐름: send-code → verify → signup 3단계 분리
- 범위: Sprint 2
- 결정:
  - `POST /api/auth/email/send-code` 호출 시 `email_verification` 테이블에 인증 코드 row 생성 (코드 해시 저장).
  - `POST /api/auth/email/verify` 호출 시 코드 매칭/만료/시도 횟수 검증 → 해당 row의 `verified_at` 갱신.
  - `POST /api/auth/signup` 호출 시 해당 이메일에 대해 유효(미만료, verified, 미사용) 인증 레코드가 있는지 확인 후 `app_user` 생성. 생성 시 `is_email_verified=1`로 박음. 사용된 인증 레코드는 폐기 처리.
- 근거: API 명세에 인증 엔드포인트가 3단계로 분리되어 있음. SRS RE-SF1-01의 추상적 표현보다 명세를 우선.
- 후속 작업: `email_verification` 테이블 신규 추가 (P-001 → D-006 에서 처리됨). 채범수 — SRS RE-SF1-01 보강 (P-003).

### D-006. SQL 스키마 정비 — 트리거/가드 제거, `email_verification` / `refresh_token` 신규 (P-001 closure)
- 범위: 전체 DB 스키마
- 결정:
  - 정식 스키마 파일을 `studymate_schema.sql` 로 일원화. 이전 임시 파일 `tmp(2).sql` 폐기.
  - D-003 적용: 트리거 7건 + 가드 테이블 3건 (`study_active_member_guard`, `study_active_leader_guard`, `application_pending_guard`) 모두 제거. 동일 비즈니스 규칙은 서비스 레이어 트랜잭션 + 락이 담당. 해당 위치는 `docs/auth-class-design.md` §10 / 본 문서 D-003 영향 항목 참고.
  - D-005 적용: `email_verification (id, email, code_hash, expires_at, verified_at, consumed_at, attempt_count, created_at)` 신규.
  - D-002 적용: `refresh_token (id, user_id, token_hash, expires_at, revoked_at, created_at)` 신규. `user_id` ↔ `app_user.id` FK CASCADE.
  - 샘플 데이터: 트리거가 자동 생성하던 멤버 row(ACCEPTED 신청 변환분, id 10~15) 명시 INSERT 로 추가. `study.current_member_count` 를 정확한 값으로 박음.
- 근거: 시서경 DB 담당 해제(2026-05-12) 로 채범수가 DB 책임 인계. P-001 닫음.
- 영향 문서: 본 파일 P-001 closed 표시. `docs/claude-context.md` §4 자료 경로 / §5 팀 구성 / §6 (DB 트리거 항목 안내 갱신).

### D-007. Logout 정책 — 단일 세션 로그아웃 (P-007 closure)
- 범위: Sprint 2 (인증)
- 결정: `POST /api/auth/logout` 호출 시 인증된 access 토큰 + 본문에 동봉된 refreshToken **한 건만** 폐기한다. 동일 사용자의 다른 디바이스 세션은 영향받지 않음.
- API 명세 변경 요구사항: Request Body 가 현재 `-` 로 표기되어 있음 → `{ "refreshToken": "string" }` 으로 갱신 필요. (시트 직접 수정은 본 세션 후 marcus 가 외부에서 진행)
- 코드 영향:
  - `AuthController.logout(@AuthenticationPrincipal CustomUserDetails, @Valid @RequestBody RefreshTokenRequest)` 시그니처 변경.
  - `AuthService.logout(long userId, String refreshToken)` 시그니처 변경.
  - `TokenService.revokeOne(long userId, String refreshToken)` 신규 (token 파싱 → token_hash 매칭 → row.userId == 인증된 userId 검증 → `revokeById`).
  - 잘못된 토큰 / 만료 시 `INVALID_TOKEN` / `TOKEN_EXPIRED` 응답.
- 근거: 디바이스/탭별 독립 로그아웃이 UX 자연스러움. 향후 모바일 클라이언트 추가 시에도 동일 모델 유지 가능.
- 후속: `docs/auth-class-design.md` §3.5 / §4 / §5.1 / §5.3 / §11.6 / §12 / §13 갱신 (본 세션 처리).

### D-008. 이메일 인증 정책 상수 확정 (P-008 closure)
- 범위: Sprint 2 (인증)
- 결정:
  - 인증 코드 길이: **6자리 숫자**
  - 코드 TTL (만료까지): **10분**
  - 동일 인증 row 의 verify 시도 횟수 상한: **5회**
  - 동일 이메일의 send-code 재발송 쿨다운: **60초**
- 근거: `auth-class-design.md` §2.3 / §3.1 의 가정값을 그대로 채택. 학교 프로젝트 규모상 더 정교한 튜닝 불필요. 향후 운영 데이터에 따라 조정 가능.
- 후속: 본 결정 로그 + `auth-class-design.md` §2.3 에만 둔다. SRS 본문/검사기준에는 박지 않는다 (검사기준서는 구현 상수 기술 문서 아님).

### D-009. 동시성 테스트 스코프 제외 — 통합 DB 는 H2
- 범위: Sprint 2 (인증) 테스트 전반
- 결정:
  - **동시성 통합 테스트를 본 스프린트 테스트 스코프에서 제외한다.** auth-class-design §10 의 세 시나리오(동시 signup / 동시 verify / 동시 rotate)는 코드 레벨 락/`@Modifying` affected-row 분기 자체로 방어되지만, 통합 테스트로 검증하지 않는다.
  - **테스트 통합 DB 는 H2** (MySQL 호환 모드) 로 확정. Testcontainers MySQL 비채택. 동시성 테스트가 빠지면 H2 의 락 시멘틱 차이는 인증 도메인 쿼리에 영향 없음.
- 근거:
  - 학교 프로젝트 규모(예상 동시 사용자 수십) 와 시연 위주 산출물 목표에서 동시성 검증의 ROI 가 낮음. 테스트 작성/유지 부담만 큼.
  - 동시성 정합성을 코드로 방어하는 것(D-003 의 트랜잭션+락, rotate 의 affected-row 분기) 자체는 그대로 유지. 단지 그 분기를 통합 테스트로 회귀 검증하지 않을 뿐.
  - H2 채택은 Docker 의존 제거 + 빌드/CI 단순화. 인증 도메인 JPQL/`@Modifying` 쿼리는 H2 도 그대로 동작.
- 영향:
  - `docs/auth-test-plan.md` §5 동시성 테스트 섹션 제거. §2 / §3 / §7 / §8 의 동시성·MySQL 관련 언급 정리. §3 DB 결정 H2 로 갱신.
  - P-002 의 "동시성 정합성 검증을 통합 테스트로" 결정은 본 D-009 로 효력 변경: 통합 테스트 자체를 안 쓴다. SRS 검사기준 RE-NF-06 (인증 관련 부분) 은 본 스프린트에서 코드 레벨 방어로만 닫고, 회귀 테스트는 두지 않는다.
- 후속: 운영 단계에서 필요 시 다시 도입. 그 시점에 본 결정을 새 D-번호로 reopen.

### D-010. 스터디 = 모집글 = 팀 스페이스 통합 모델 (별도 team 테이블 없음)
- 범위: Sprint 3 ~ 전체
- 결정:
  - 도메인 개념 "스터디", "모집글", "팀 전용 공간" 셋은 모두 동일한 `study` row 의 라이프사이클 표현이다. 별도 `team` 테이블/엔티티는 두지 않는다.
  - SRS RE-SF4-01 "팀 전용 공간 자동 생성" 의 구현 의미는 *별도 자원 생성 없음, 같은 `study` row 의 `status` 가 OPEN → CLOSED 로 전이되는 것* 으로 본다.
  - API URL 의 `/api/teams/{teamId}/...` 는 같은 study 에 대한 *활동 단계 API 별명*. 응답 본문에서 `teamId` 가 필요한 자리에는 `studyId` 와 동일한 값을 채워 응답한다.
  - DB 스키마 변경 없음. `post` / `post_comment` / `attendance_session` / `attendance_record` 의 FK 는 `study_id` 그대로 유지.
- 근거:
  - 학교 프로젝트 규모상 team 이 study 와 독립적으로 가져야 할 메타데이터가 현재 없음. team 테이블을 두면 study 와 1:1 FK 이상의 역할을 하지 못해 모델 복잡도만 증가.
  - 통합 모델은 트랜잭션 경계가 단순하고 마이그레이션 부담이 0. SRS 의 "팀" 어휘는 도메인 인스턴스가 아닌 라이프사이클 *단계*로 해석.
  - SRS 자체에는 "팀이 study 와 별개 엔티티" 라는 명시는 없음. RE-SF2-01 / RE-SF4-01 의 표현이 단계 전이로 자연스럽게 읽힘.
- 영향:
  - **작업 시트 4주차 #29 "팀 전용 공간 생성" → 별도 작업 없음.** 시트 비고에 *"D-010 에 따라 별도 자원 생성 없음 (study 생성/마감으로 충족)"* 명시 필요.
  - 작업 시트 #23 ~ #25 비고에 본 결정 명시 필요.
  - API 명세 시트: `/api/teams/{teamId}/...` 엔드포인트의 `teamId` 파라미터 의미를 *`studyId` 와 동일* 로 비고에 명시 필요.
  - "내 스터디 목록 조회" 응답의 `teamId` 필드는 `studyId` 와 동일한 값으로 채움.
- 후속: 외부 문서(시트) 반영은 본 결정 이후 marcus 가 외부에서 진행.

### D-011. 모집 인원(maxMembers) 변경 룰
- 범위: Sprint 3 (스터디 수정)
- 결정:
  - `PATCH /api/studies/{id}` 의 `maxMembers` 변경 시 `maxMembers ≥ current_member_count` 가 항상 보장되어야 한다.
  - 위반 시 응답: `409 INVALID_MAX_MEMBERS`, message: *"현재 멤버 수보다 작게 줄일 수 없습니다. 먼저 팀원을 강퇴해 주세요."*. 새 `ErrorCode.INVALID_MAX_MEMBERS` 신설.
  - 더 작게 줄이려면 사전에 강퇴 (RE-SF4-04, 4주차) 처리 필요. 본 스프린트 응답에서 그 안내만.
- 근거: 활성 멤버 수가 모집 정원을 초과하는 inconsistency 방지. SRS 직접 표현은 없지만 자연스러운 운영 규칙이며, marcus 가 본 세션에서 명시.
- 후속:
  - `PATCH /api/studies/{id}` 의 `maxMembers` 필드 검증 로직 (서비스 레이어, `study` row 락 하에).
  - 새 `ErrorCode.INVALID_MAX_MEMBERS` (HTTP 409) 추가.
  - API 명세 시트의 "스터디 수정" row 에러 칼럼에 본 케이스 추가 필요.

### D-012. 스터디 생성 시 LEADER `study_member` row 동시 생성 (트랜잭션 내)
- 범위: Sprint 3 (스터디 생성)
- 결정:
  - `POST /api/studies` 처리 시 `study` row 와 `study_member`(`role_code='LEADER'`, `is_active=1`) row 를 **같은 트랜잭션 안에서 동시 생성**한다.
  - `study.current_member_count` 는 생성 시점에 `1` 로 박는다.
  - 태그가 있다면 `study_tag` row 도 같은 트랜잭션에 INSERT.
- 근거: D-003 (트리거 금지) 의 직접적 후속. 이전 트리거가 자동 INSERT 하던 LEADER 멤버 row 와 카운트 증분을 서비스 레이어가 명시적으로 책임짐.
- 후속:
  - `StudyService.create(...)` 의 트랜잭션 경계는 `study` + `study_member` + `study_tag` 까지 한 단위.
  - SRS RE-SF2-01 본문 *"개설자는 스터디장(리더)으로 자동 지정된다"* 의 구현 매핑.

### D-013. SRS 입력 필드 정합 — `durationWeeks` / `languages` API body 보강 (필수)
- 범위: Sprint 3 (스터디 생성 / 수정 / 상세 / 목록)
- 결정:
  - SRS RE-SF2-01 의 7개 입력 필드 (스터디명·소개·모집 인원·활동 주기·활동 기간·관심 태그·사용 언어) 가 API 명세 시트의 `POST /api/studies` body 에 모두 반영되도록 한다. 현재 누락된 두 필드는 **둘 다 필수**로 추가한다.
  - 보강 필드:
    - `durationWeeks: int` — 활동 기간(주). 양의 정수. `>= 1`.
    - `languages: string[]` — 사용 언어 태그 배열. 빈 배열 불가 (최소 1개). 회원 프로필 언어와 동일한 어휘 사용.
  - 적용 위치:
    - `POST /api/studies` request body: 두 필드 추가, 필수.
    - `PATCH /api/studies/{id}` request body: 두 필드 옵셔널(부분 수정, 미전송 시 변경 없음).
    - `GET /api/studies/{id}` response: 두 필드 포함.
    - `GET /api/studies` (목록) response: 카드용 요약이라 본 스프린트에선 미포함 (필요 시 다음 스프린트 추가).
- 근거: SRS 가 입력으로 정의한 필드가 API 명세에 빠져 있으면 검사기준 RE-SF2-01 ("입력한 스터디 정보와 일치") 가 검증 불가. SRS 를 진실로 두고 명세를 맞춤.
- 후속:
  - DB: `study.duration_weeks INT NOT NULL`, `study_language(study_id, language_code)` 신규 테이블. `study_tag` 와 동일한 패턴.
  - API 명세 시트: 본 결정 이후 marcus 가 외부에서 시트 갱신.
  - 응답 스키마 정비도 본 결정에 묶음 — 상세 조회 응답에 누락된 `durationWeeks` / `languages` / `status` 일괄 포함.

### D-014. 모집 상태 전이 — OPEN ↔ CLOSED 양방향 허용 (재오픈 가능)
- 범위: Sprint 3 (`PATCH /api/studies/{id}` status 변경)
- 결정:
  - 스터디장이 `PATCH` 로 `status` 를 `OPEN` ↔ `CLOSED` 양방향으로 전환할 수 있다. 실수 마감 복구 / 추가 모집 재개 시나리오 지원.
  - 단, `CLOSED → OPEN` 재오픈 시점에 `current_member_count >= maxMembers` 이면 모집이 즉시 다시 가득 차므로 의미상 무효 → **`409 INVALID_STATUS_TRANSITION`** 응답. (이 케이스만 차단.)
  - 정원 도달에 의한 자동 마감(5주차 신청 수락 흐름) 은 본 결정과 독립. 자동 마감 후의 수동 재오픈 자체는 허용.
- 근거: 학교 프로젝트 운영상 마감 실수 복구가 합리적 요구. SRS RE-SF2-02 의 "마감 후 기존 팀 구성에 영향 주는 수정 제한" 은 `maxMembers` 축소 같은 멤버 영향 변경에 대한 것이고, 단순 상태 토글은 팀 구성에 영향 없음.
- 후속:
  - `ErrorCode.INVALID_STATUS_TRANSITION` (HTTP 409) 신규.
  - API 명세 시트의 "모집 마감" row 설명을 *"스터디장이 모집 상태를 변경한다 (OPEN ↔ CLOSED)"* 로 갱신 + `INVALID_STATUS_TRANSITION` 에러 케이스 추가.
  - 본 결정에 따라 시트의 "스터디 수정" / "모집 마감" 두 row 는 사실상 같은 엔드포인트(PATCH) 의 두 사용 패턴 — 한 핸들러에서 처리 (claude-context §1 의 통합 처리와 정합).

### D-015. Sprint 3 구현 디폴트 묶음 (Marcus 결정 위임 → 일괄 채택)
- 범위: Sprint 3 (스터디 CRUD) 구현 디테일
- 결정 (한 묶음으로 채택, 향후 운영에서 필요 시 개별 재검토):
  - **목록 조회 (`GET /api/studies`)**
    - 인증: **필수** (Bearer 토큰). 모든 스터디 API 인증 필수로 통일.
    - 기본 정렬: **`created_at DESC`** (SRS RE-SF2-03 "최신순").
    - status 필터: **본 스프린트는 OPEN 만 노출**. (CLOSED 도 보고 싶다는 요구는 검색/내 스터디 등 다음 스프린트 도메인에서 다룸.)
    - 페이징 디폴트: `page=0, size=20`. `size` 상한 `100`.
  - **상세 조회 (`GET /api/studies/{id}`)**: 인증 필수. 본인 LEADER 여부 등 사용자 컨텍스트 필드는 본 스프린트에선 응답에 포함하지 않음 (마이페이지 / 내 스터디 도메인 책임).
  - **PATCH 시멘틱**: **필드 미전송 = 변경 없음 / 필드 명시 = 해당 값으로 변경**. `null` 명시는 본 스프린트에선 *유효하지 않은 요청* 으로 거절 (400 INVALID_INPUT) — 모든 필드가 not-null 비즈니스 필드라 null 의 의미가 없음.
  - **마감 후 수정 허용 필드**: `CLOSED` 상태에서도 `title` / `description` / `tags` / `languages` 수정 허용. `maxMembers` / `durationWeeks` / `meetingCycle` 은 팀 구성·운영에 영향 → **마감 상태에선 거절 (`409 INVALID_STATUS_FOR_UPDATE`)**.
  - **maxMembers 범위**: `2 ≤ maxMembers ≤ 50`. 하한 2 (개설자 LEADER 1명 외 최소 1명 모집 필요), 상한 50 (학교 프로젝트 규모).
  - **durationWeeks 범위**: `1 ≤ durationWeeks ≤ 52`.
  - **meetingCycle**: **freeform string, 최대 50자**. enum 까지 박으면 표현력 제약. UI 에서 추천값 (주1회/주2회/격주/월1회 …) 정도만 안내.
  - **title 길이**: 최소 2, 최대 50자.
  - **description 길이**: 최대 2000자.
  - **tags / languages**: 각 배열 원소 길이 1~30자, 배열당 최대 10개, 중복 불가 (서버에서 dedupe 후 비교).
  - **DELETE 방식**: **soft delete**. `study.is_deleted = 1` 로 표시, row 물리 삭제하지 않음. 모든 조회/수정/신청 쿼리는 `is_deleted = 0` 조건을 기본 깔고 동작. 본 스프린트 시점엔 자식 테이블(`study_member` / `study_tag` / `study_language`) 도 그대로 두고 부모 플래그만 변경 — 자식 데이터를 보존해야 향후 통계/히스토리 활용 가능. 이후 도메인 (application / post / attendance 등) 추가 시 각자 `is_deleted` 조건을 자기 쿼리에 끼워 넣는다.
  - **응답 ↔ DB 필드명 정합**: API 명세 어휘를 진실로 두고 DTO 매핑에서 변환. DB `purpose` ↔ API `description`, DB `activity_cycle` ↔ API `meetingCycle`. ALTER 로 컬럼명 바꿀 만큼의 가치는 없음 (스키마 안정성 우선).
  - **`study.status` ENUM**: `CANCELLED` 값은 본 스프린트에서 사용하지 않음. 향후 별도 결정 시까지 그대로 두되 서비스 레이어는 `OPEN` / `CLOSED` 두 값만 생산.
- 근거: 위 항목들은 marcus 가 위임한 디폴트. 모두 학교 프로젝트 규모/시연 우선 원칙에 정합. 실 운영 도입 시 개별 결정으로 재오픈.
- 후속:
  - `ErrorCode.INVALID_STATUS_FOR_UPDATE` (HTTP 409) 신규.
  - DTO Bean Validation 어노테이션 / 응답 페이지 구조는 본 결정값 그대로 적용.

### D-016. 스키마 컬럼 타입 불일치 해결 — TINYINT UNSIGNED → INT UNSIGNED 변환
- 범위: 전체 DB 스키마 (MySQL)
- 결정:
  - MySQL 테이블에서 사용된 `TINYINT UNSIGNED` 타입 컬럼들을 `INT UNSIGNED`로 변경한다.
  - 대상 컬럼:
    - `study.max_members`
    - `study.current_member_count`
    - `study.duration_weeks`
    - `study_role.sort_order`
    - `attendance_session.session_num`
  - H2는 자동 생성 방식(create-drop)이므로 별도 대처 불필요. 실제 MySQL DB(로컬 및 운영 EC2)에 대해 다음 ALTER 명령을 실행하여 마이그레이션한다:
    ```sql
    ALTER TABLE study_role MODIFY COLUMN sort_order INT UNSIGNED NOT NULL DEFAULT 99 COMMENT '정렬값';
    ALTER TABLE study MODIFY COLUMN max_members INT UNSIGNED NOT NULL DEFAULT 10 COMMENT '최대 멤버 수';
    ALTER TABLE study MODIFY COLUMN current_member_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '현재 활성 멤버 수 (서비스 레이어 유지, D-003)';
    ALTER TABLE study MODIFY COLUMN duration_weeks INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '활동 기간(주) — D-013';
    ALTER TABLE attendance_session MODIFY COLUMN session_num INT UNSIGNED NOT NULL COMMENT '세션 번호';
    ```
- 근거: Java의 `int` / `Integer` 타입 필드가 Hibernate ddl-auto: validate 유효성 검사 시 MySQL의 `TINYINT UNSIGNED` 컬럼과 매핑될 때 `INTEGER` 기대 타입 불일치(wrong column type encountered) 에러가 발생하여 앱 기동이 실패함. 이를 일치시키기 위해 스키마와 데이터베이스 컬럼의 물리적 크기를 `INT UNSIGNED`로 넓힘.
- 후속: `studymate_schema.sql` 갱신 및 EC2 MySQL DB에 ALTER 명령어 적용 완료.

### D-017. 스키마 컬럼 타입 불일치 해결 — study_member.left_reason 컬럼 ENUM -> VARCHAR(20) 변환
- 범위: 전체 DB 스키마 (MySQL)
- 결정:
  - MySQL `study_member` 테이블의 `left_reason` 컬럼 타입을 `ENUM('VOLUNTARY','KICKED','STUDY_CLOSED')`에서 `VARCHAR(20)`로 변경한다.
- 근거: Java `StudyMember` 엔티티 클래스에서 `leftReason` 필드가 `String`(`VARCHAR(20)`)으로 매핑되어 있으나, MySQL 데이터베이스에는 `ENUM` 타입으로 선언되어 있어 Hibernate `ddl-auto: validate` 유효성 검증 시 타입 불일치 에러(`wrong column type encountered`)가 발생함. 이를 일치시키기 위해 데이터베이스 컬럼 타입을 `VARCHAR(20)`로 맞춘다.
- 후속: `studymate_schema.sql` 수정 완료.

---

## 2. 미해결 / 정합화 필요 항목 (Pending Discrepancies)

### P-001. SQL 스키마 보강 — `email_verification`, `refresh_token` 신규 필요
- 출처: API 명세 ↔ `studymate_schema.sql`
- 담당: 채범수 (2026-05-12 시서경 → 채범수 인계)
- 상태: **CLOSED → D-006** (2026-05-12).

### P-002. SRS RE-NF-06 검사기준 — DB 트리거 제거에 따른 재작성
- 출처: SRS §6 검사기준서(비기능) RE-NF-06
- 문제: 검사 절차가 모호할 수 있고, 트리거 기반 강제력을 암묵적으로 전제할 가능성. 트리거 제거(D-003) 결과로 어떻게 검증할지 구체화 필요.
- 결론 (2026-05-12):
  - **SRS 검사기준 본문은 변경 불필요.** 기존 문구("모집 인원이 1명 남은 스터디에 동시에 2명이 지원-수락 처리를 시도한다 / 삭제된 스터디에 지원을 시도한다 / 정원 초과 수락이 발생하지 않는다 / ...") 가 이미 사용자 관점 시나리오로 기술되어 있어 트리거 유무와 무관하게 적용 가능. 검사기준서에는 구현 도구/계층을 적지 않는다.
  - **내부 결정 (구현 측 검증 방법)**: JMeter / k6 도입 없이 Spring Boot 통합 테스트 내 멀티스레드(`ExecutorService` + `CountDownLatch`) 로 정합성 검증. 부하 측정 도구가 아니라 정합성 검증이 목적이므로 통합 테스트로 충분. 이 결정은 `docs/auth-class-design.md` §10 (동시성 시나리오) 및 본 결정 로그 D-003 영향 항목에 묶어 두고, 본격적인 테스트 플랜은 다음 단계(`engineering:testing-strategy` 스킬) 진입 시점에 정리.
- 담당: 채범수
- 상태: **CLOSED** (SRS 변경 없음; 내부 결정 본 항목으로 충분).

### P-003. SRS RE-SF1-01 — 이메일 인증 절차 구체화
- 출처: SRS §3 요구사항(기능) RE-SF1-01 + SRS §5 검사기준서(기능) No.1
- 문제: SRS 본문은 "이메일 인증을 통해 재학생 여부를 확인한다"고만 기술. 검사기준 본문은 "**인증 메일 수신 후 인증 링크를 클릭**" 으로 적혀 있어 D-005(코드 입력 방식) 와 모순. 둘 다 손봐야 함.
- 확정된 수정안 (2026-05-12, SRS Google Docs 적용 대기):
  - **§3 RE-SF1-01 요구사항 설명** (행위 수준 — 상수는 박지 않음):
    > 학교 이메일(@university.ac.kr) 기반으로 회원가입을 처리한다. 재학생 여부 확인은 (1) 이메일로 인증 코드 발송, (2) 사용자가 입력한 코드의 검증, (3) 검증을 완료한 이메일로의 회원가입의 3단계 절차로 수행하며, 회원가입 시점에는 사전에 코드 검증을 통과한 이메일이어야 한다. 비밀번호는 영문과 숫자를 각각 1자 이상 포함하며, 8자 이상 20자 이하로 설정해야 한다.
  - **§5 No.1 검사기준** (사용자 관점 — API명/HTTP 코드/도구 표기 없음):
    - 검사방법: 학교 이메일(@university.ac.kr)로 인증 코드 발송을 요청한다. 수신된 인증 코드를 입력해 검증을 완료한 뒤, 검증된 이메일과 비밀번호, 이름으로 회원가입을 진행한다. 별도로 코드 검증을 거치지 않은 이메일로 회원가입을 시도한다.
    - 예상결과: 정상 흐름에서는 계정이 생성되고 로그인 가능 상태가 된다. 코드 검증을 거치지 않은 이메일의 회원가입은 거절된다.
    - 판정기준: 정상 가입 후 해당 이메일로 로그인 성공 여부 확인. 미인증 이메일 회원가입 시도가 거절되는지 확인.
  - 인증 정책 상수(6자리/10분/5회/60초)는 SRS 에 박지 않는다 — `auth-class-design.md` §2.3 와 본 로그 D-008 에 두는 걸로 충분.
- 담당: 채범수
- 상태: **CLOSED** (2026-05-12, SRS Google Docs 적용 완료).

### P-004. 작업 시트 #15 — 회원가입 시점 관심 태그 저장 표현 수정
- 출처: 프로젝트 관리 시트 ↔ API 명세
- 확정된 수정안 (2026-05-12, 시트 적용 대기):
  - 할 일 명: "프로필 관심태그 저장 (회원가입시)" → **"프로필 관심태그 / 사용 언어 저장 (마이페이지 PATCH)"**
  - 주차: 미정 → **마이페이지 구현 주차로 재배치**
  - 비고: **"D-004: 회원가입 호출에는 포함되지 않음. 가입 직후 PATCH /api/mypage/profile 로 분리 저장. RE-SF1-03 / RE-SF1-04 연계."**
- 담당: 채범수
- 상태: **CLOSED** (2026-05-12, 작업 시트 적용 완료).

### P-005. 요구사항 추적표 갱신
- 출처: SRS §7 (요구사항 추적표 별첨) ↔ API 명세 ↔ UML
- 문제: 각 RE-SF*-* 식별자가 어느 API 엔드포인트와 어떤 구현 클래스/테스트로 연결되는지 추적표 갱신 미완.
- 필요 조치: Sprint 2 종료 시점에 추적표 일괄 갱신 (요구사항 → API → 컨트롤러/서비스 → 테스트).
- 담당: 모두
- 상태: Sprint 2 종료 후 정리 (본 세션 처리 대상 아님).

### P-006. `POST /api/auth/signup` — 이메일 미인증 에러 케이스 누락
- 출처: API 명세 시트 ↔ D-005 ↔ `docs/auth-class-design.md` §3.2
- 문제: 명세 시트의 signup 응답 코드는 400 `INVALID_INPUT` / 409 `CONFLICT` 두 개. D-005에 의해 "유효한 이메일 인증 레코드 없음" 케이스가 반드시 발생함에도 명세에 빠져 있음.
- 확정된 수정안 (2026-05-12, 시트 적용 대기):
  - API 명세 시트 회원가입 row 에러 칼럼에 다음 케이스 추가:
    > 400: `{ "code": "EMAIL_NOT_VERIFIED", "message": "이메일 인증이 완료되지 않았습니다." }`
  - 코드 측: `ErrorCode.EMAIL_NOT_VERIFIED` (HTTP 400) 는 `auth-class-design.md` §5.2 / §9 에 이미 반영되어 있음 (코드 작성 단계에서 적용).
- 담당: 채범수
- 상태: **CLOSED** (2026-05-12, API 명세 시트 적용 완료).

### P-007. `POST /api/auth/logout` — Refresh 폐기 범위 정책 미정
- 출처: API 명세 시트 ↔ `docs/auth-class-design.md` §3.5, §4
- 담당: 채범수
- 상태: **CLOSED → D-007** (2026-05-12, "단일 세션 로그아웃" 채택).

### P-008. Email Verification 정책 상수 미명시
- 출처: API 명세 시트 ↔ SRS RE-SF1-01 ↔ `docs/auth-class-design.md` §2.3, §3.1
- 담당: 채범수
- 상태: **CLOSED → D-008** (2026-05-12, 가정값 그대로 채택).

---

## 사용 가이드 (팀원용)

작업 중 SRS / SDP / API 명세 / SQL 스키마 / UML 사이에 모순이 발견되면 본 파일에 추가한다.

- "확정된 결정"은 합의 후 등록한다. 결정에는 범위(스프린트/기능), 근거, 영향 받는 다른 문서를 같이 적는다.
- "미해결" 항목은 담당자와 현재 상태를 명시한다. 해결되면 해당 항목을 "확정된 결정"으로 옮기거나, 사유와 함께 닫는다.
- 결정은 덮어쓰지 말고 변경 이력 표에 한 줄씩 추가한다.
- 식별자 규칙: 결정은 `D-NNN`, 미해결은 `P-NNN`. 채번은 단조 증가.
