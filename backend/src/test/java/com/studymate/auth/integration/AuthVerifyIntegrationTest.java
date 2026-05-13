package com.studymate.auth.integration;

import com.studymate.auth.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthVerifyIntegrationTest extends AuthIntegrationTestBase {

    // T-VRFY-01: 정확한 코드 → 200, verifiedAt != null, attemptCount = 0
    @Test
    void T_VRFY_01() throws Exception {
        sendCode(TEST_EMAIL);
        String code = fakeEmailSender.lastCode();

        MvcResult res = verifyCode(TEST_EMAIL, code);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(row.get("verified_at")).isNotNull();
        assertThat(row.get("attempt_count")).isEqualTo(0);
    }

    // T-VRFY-02: 잘못된 코드 → 400 INVALID_EMAIL_CODE, attemptCount = 1, verifiedAt = null
    @Test
    void T_VRFY_02() throws Exception {
        sendCode(TEST_EMAIL);

        MvcResult res = verifyCode(TEST_EMAIL, "000000");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_EMAIL_CODE");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(row.get("verified_at")).isNull();
        assertThat(row.get("attempt_count")).isEqualTo(1);
    }

    // T-VRFY-03: 잘못된 코드 5회 → 6번째 정답도 거절 (MAX_ATTEMPTS 경계)
    @Test
    void T_VRFY_03() throws Exception {
        sendCode(TEST_EMAIL);
        String correct = fakeEmailSender.lastCode();
        // 정답과 다른 코드 (correct 가 000001 이면 wrong = 000002, etc.)
        String wrong = correct.equals("000000") ? "999999" : "000000";

        // 1~5회 오답
        for (int i = 1; i <= 5; i++) {
            MvcResult res = verifyCode(TEST_EMAIL, wrong);
            assertThat(res.getResponse().getStatus()).isEqualTo(400);
            assertThat(errorCode(res)).isEqualTo("INVALID_EMAIL_CODE");
        }

        // attemptCount = 5 확인
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(row.get("attempt_count")).isEqualTo(5);

        // 6번째 정답 → 시도 초과로 거절
        MvcResult res6 = verifyCode(TEST_EMAIL, correct);
        assertThat(res6.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res6)).isEqualTo("INVALID_EMAIL_CODE");

        Map<String, Object> row6 = jdbcTemplate.queryForMap(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(row6.get("verified_at")).isNull();
    }

    // T-VRFY-04: 만료된 row + 정확한 코드 → 400 EXPIRED_EMAIL_CODE, verifiedAt = null
    @Test
    void T_VRFY_04() throws Exception {
        // 만료된 EV row 직접 삽입
        String code = "123456";
        insertEv(TEST_EMAIL, HashUtils.sha256(code),
                 Instant.now().minus(1, ChronoUnit.MINUTES),  // expiresAt: 1분 전 (만료)
                 null, null, 0, Instant.now().minus(11, ChronoUnit.MINUTES));

        MvcResult res = verifyCode(TEST_EMAIL, code);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("EXPIRED_EMAIL_CODE");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(row.get("verified_at")).isNull();
    }

    // T-VRFY-05: 미소비 row 없음 → 400 INVALID_EMAIL_CODE
    @Test
    void T_VRFY_05() throws Exception {
        MvcResult res = verifyCode(TEST_EMAIL, "123456");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_EMAIL_CODE");
    }

    // T-VRFY-06: 오래된 row + 최신 row → 최신 row 만 매칭 (findTopBy...OrderByCreatedAtDesc)
    @Test
    void T_VRFY_06() throws Exception {
        // 오래된 row 삽입 (코드: 111111, created_at: 1시간 전)
        String oldCode = "111111";
        insertEv(TEST_EMAIL, HashUtils.sha256(oldCode),
                 Instant.now().plus(9, ChronoUnit.MINUTES),   // 아직 유효
                 null, null, 0,
                 Instant.now().minus(1, ChronoUnit.HOURS));   // created_at 오래됨

        // 최신 row: sendCode 호출 (현재 시각 → created_at 최신)
        sendCode(TEST_EMAIL);
        String newCode = fakeEmailSender.lastCode();

        // 최신 row 코드로 verify → 200 성공
        MvcResult res = verifyCode(TEST_EMAIL, newCode);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        // 최신 row(verifiedAt != null) + 오래된 row(verifiedAt = null) 확인
        int verifiedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification WHERE email = ? AND verified_at IS NOT NULL",
                Integer.class, TEST_EMAIL);
        assertThat(verifiedCount).isEqualTo(1);

        int oldCodeVerified = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification WHERE email = ? AND code_hash = ? AND verified_at IS NOT NULL",
                Integer.class, TEST_EMAIL, HashUtils.sha256(oldCode));
        assertThat(oldCodeVerified).isZero();
    }
}
