package com.studymate.auth.integration;

import com.studymate.auth.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSignupIntegrationTest extends AuthIntegrationTestBase {

    // T-SIGN-01: EV verified+미소비+미만료 → 201, app_user 생성, EV consumedAt != null
    @Test
    void T_SIGN_01() throws Exception {
        sendCode(TEST_EMAIL);
        String code = fakeEmailSender.lastCode();
        verifyCode(TEST_EMAIL, code);

        MvcResult res = signup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("email")).isEqualTo(TEST_EMAIL);
        assertThat(body.get("name")).isEqualTo(TEST_NAME);
        assertThat(body.get("userId")).isNotNull();

        // app_user 저장 확인
        Map<String, Object> userRow = jdbcTemplate.queryForMap(
                "SELECT * FROM app_user WHERE email = ?", TEST_EMAIL);
        String hash = (String) userRow.get("password_hash");
        assertThat(hash).isNotEqualTo(TEST_PASSWORD);         // 평문 저장 금지
        assertThat(hash).startsWith("$2a$");                  // BCrypt 패턴
        assertThat(userRow.get("is_email_verified")).isIn(true, (byte) 1);
        assertThat(userRow.get("is_deleted")).isIn(false, (byte) 0);

        // EV consumedAt 설정 확인
        Map<String, Object> evRow = jdbcTemplate.queryForMap(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(evRow.get("consumed_at")).isNotNull();
    }

    // T-SIGN-02: EV row 없음 → 400 EMAIL_NOT_VERIFIED, user 미생성
    @Test
    void T_SIGN_02() throws Exception {
        MvcResult res = signup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(userRepository.count()).isZero();
    }

    // T-SIGN-03: EV row 있으나 verifiedAt = null → 400 EMAIL_NOT_VERIFIED
    @Test
    void T_SIGN_03() throws Exception {
        sendCode(TEST_EMAIL);  // verifiedAt 미설정 상태

        MvcResult res = signup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    // T-SIGN-04: EV row consumedAt != null (재사용) → 400 EMAIL_NOT_VERIFIED
    @Test
    void T_SIGN_04() throws Exception {
        // verified + consumed EV row 삽입
        Instant now = Instant.now();
        insertEv(TEST_EMAIL, HashUtils.sha256("123456"),
                 now.plus(10, ChronoUnit.MINUTES),  // expiresAt
                 now.minus(1, ChronoUnit.MINUTES),   // verifiedAt
                 now,                                // consumedAt (이미 소비)
                 0, now.minus(11, ChronoUnit.MINUTES));

        MvcResult res = signup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    // T-SIGN-05: EV verified 후 expiresAt 경과 → 400 EMAIL_NOT_VERIFIED
    @Test
    void T_SIGN_05() throws Exception {
        Instant now = Instant.now();
        // expiresAt 이 과거 → 만료된 row
        insertEv(TEST_EMAIL, HashUtils.sha256("123456"),
                 now.minus(1, ChronoUnit.MINUTES),   // expiresAt: 만료됨
                 now.minus(5, ChronoUnit.MINUTES),   // verifiedAt: 유효했던 시점
                 null, 0,
                 now.minus(11, ChronoUnit.MINUTES));

        MvcResult res = signup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    // T-SIGN-07: 탈퇴 후 동일 이메일 재가입 → 201, 신규 active user row 생성
    // - 회귀: 소프트삭제된 사용자의 이메일은 재사용 가능해야 함
    // - 실제 플로우 사용: full signup → DELETE /api/profile → re-signup
    @org.springframework.beans.factory.annotation.Autowired
    com.studymate.profile.service.ProfileService profileService;

    @Test
    void T_SIGN_07_탈퇴_후_동일_이메일_재가입() throws Exception {
        // 1) 기존 사용자: full signup 으로 정상 가입
        doFullSignup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        long oldUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", Long.class, TEST_EMAIL);

        // 2) 탈퇴 (실제 서비스 호출 — email sentinel 치환 / PII 익명화 포함)
        profileService.deleteProfile(oldUserId);

        // 탈퇴 row 의 email 이 sentinel 로 치환됐는지 확인
        String oldEmailNow = jdbcTemplate.queryForObject(
                "SELECT email FROM app_user WHERE id = ?", String.class, oldUserId);
        assertThat(oldEmailNow).isNotEqualTo(TEST_EMAIL);
        assertThat(oldEmailNow).startsWith("__deleted_");

        // 3) 동일 이메일로 재가입 (EV 쿨다운 회피 위해 EV 테이블 정리)
        emailVerificationRepository.deleteAll();
        fakeEmailSender.clear();
        sendCode(TEST_EMAIL);
        String code = fakeEmailSender.lastCode();
        verifyCode(TEST_EMAIL, code);

        MvcResult res = signup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        // 신규 active user row 가 별도 id 로 생성
        Long newUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ? AND is_deleted = false",
                Long.class, TEST_EMAIL);
        assertThat(newUserId).isNotEqualTo(oldUserId);

        // 기존 탈퇴 row 유지 (감사 로그 보존)
        Long deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE id = ? AND is_deleted = true",
                Long.class, oldUserId);
        assertThat(deletedCount).isEqualTo(1L);
    }

    // T-SIGN-06: EV OK + 동일 이메일 user 존재 → 409 CONFLICT, EV consumedAt != null (소비 확정)
    @Test
    void T_SIGN_06() throws Exception {
        // EV row: verified + 미소비 + 미만료
        Instant now = Instant.now();
        insertEv(TEST_EMAIL, HashUtils.sha256("123456"),
                 now.plus(10, ChronoUnit.MINUTES),
                 now.minus(1, ChronoUnit.MINUTES),
                 null, 0,
                 now.minus(2, ChronoUnit.MINUTES));

        // 동일 이메일 user 존재
        saveUser(TEST_EMAIL, TEST_PASSWORD);

        MvcResult res = signup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("CONFLICT");

        // EV consumedAt 이 커밋됐는지 확인 (재인증 필요 의도)
        Map<String, Object> evRow = jdbcTemplate.queryForMap(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(evRow.get("consumed_at")).isNotNull();
    }
}
