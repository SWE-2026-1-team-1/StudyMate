package com.studymate.auth.integration;

import com.studymate.auth.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthLoginIntegrationTest extends AuthIntegrationTestBase {

    // T-LOGIN-01: 정상 로그인 → 200 {accessToken, refreshToken, userId}, refresh_token row 신규
    @Test
    void T_LOGIN_01() throws Exception {
        doFullSignup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        fakeEmailSender.clear();

        MvcResult res = login(TEST_EMAIL, TEST_PASSWORD);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        String accessToken  = (String) body.get("accessToken");
        String refreshToken = (String) body.get("refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(body.get("userId")).isNotNull();

        // refresh_token row 1건 신규 (tokenHash = sha256(refreshToken), revokedAt = null)
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NULL",
                Long.class, HashUtils.sha256(refreshToken));
        assertThat(count).isEqualTo(1);
    }

    // T-LOGIN-02: 미존재 이메일 → 401 INVALID_CREDENTIALS, refresh_token 미생성
    @Test
    void T_LOGIN_02() throws Exception {
        MvcResult res = login("nobody@university.ac.kr", TEST_PASSWORD);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(refreshTokenRepository.count()).isZero();
    }

    // T-LOGIN-03: 잘못된 비밀번호 → 401 INVALID_CREDENTIALS, 메시지 T-LOGIN-02와 동일 (user 존재 누설 방지)
    @Test
    void T_LOGIN_03() throws Exception {
        doFullSignup(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
        fakeEmailSender.clear();

        MvcResult res = login(TEST_EMAIL, "WrongPass1");
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_CREDENTIALS");

        MvcResult res2 = login("nobody@university.ac.kr", "WrongPass1");
        // 동일한 에러 코드 (메시지도 동일해야 함)
        assertThat(errorCode(res2)).isEqualTo(errorCode(res));

        assertThat(refreshTokenRepository.count()).isZero();
    }

    // T-LOGIN-04: isDeleted=true user → 401 INVALID_CREDENTIALS
    @Test
    void T_LOGIN_04() throws Exception {
        // soft delete 된 user 직접 삽입
        jdbcTemplate.update(
                "INSERT INTO app_user (email, password_hash, name, is_email_verified, is_deleted, created_at, updated_at) VALUES (?,?,?,?,?,NOW(),NOW())",
                TEST_EMAIL, "$2a$12$hash", TEST_NAME, true, true);

        MvcResult res = login(TEST_EMAIL, TEST_PASSWORD);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_CREDENTIALS");
    }
}
