package com.studymate.auth.integration;

import com.studymate.auth.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthLogoutIntegrationTest extends AuthIntegrationTestBase {

    // T-LOGOUT-01: 2개 디바이스 로그인 후 A 로그아웃 → A revokedAt != null, B revokedAt = null
    @Test
    void T_LOGOUT_01() throws Exception {
        // 기기 A 로그인
        Map<String, Object> tokensA = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String accessA  = (String) tokensA.get("accessToken");
        String refreshA = (String) tokensA.get("refreshToken");

        // 기기 B 로그인 (새 refresh 발급)
        MvcResult loginB = login(TEST_EMAIL, TEST_PASSWORD);
        Map<?, ?> bodyB  = objectMapper.readValue(loginB.getResponse().getContentAsString(), Map.class);
        String refreshB  = (String) bodyB.get("refreshToken");

        // A 로그아웃
        MvcResult res = logout(accessA, refreshA);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        // A: revokedAt != null
        Long aRevoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NOT NULL",
                Long.class, HashUtils.sha256(refreshA));
        assertThat(aRevoked).isEqualTo(1);

        // B: revokedAt = null (단일 세션 D-007)
        Long bActive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NULL",
                Long.class, HashUtils.sha256(refreshB));
        assertThat(bActive).isEqualTo(1);
    }

    // T-LOGOUT-02: 인증 헤더 없음 → 401 UNAUTHORIZED, DB 변화 없음
    @Test
    void T_LOGOUT_02() throws Exception {
        Map<String, Object> tokens = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String refreshA = (String) tokens.get("refreshToken");
        long before = refreshTokenRepository.count();

        MvcResult res = mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshA))))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("UNAUTHORIZED");
        assertThat(refreshTokenRepository.count()).isEqualTo(before);
    }

    // T-LOGOUT-03: user1 access + user2 refresh → 401 INVALID_TOKEN, user2 row 변화 없음
    @Test
    void T_LOGOUT_03() throws Exception {
        // user1 가입 + 로그인
        Map<String, Object> tokens1 = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String accessUser1 = (String) tokens1.get("accessToken");

        // user2 가입 + 로그인
        String email2 = "user2@university.ac.kr";
        Map<String, Object> tokens2 = doFullLogin(email2, TEST_PASSWORD);
        String refreshUser2 = (String) tokens2.get("refreshToken");

        MvcResult res = logout(accessUser1, refreshUser2);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_TOKEN");

        // user2 row 변화 없음
        Long user2Active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NULL",
                Long.class, HashUtils.sha256(refreshUser2));
        assertThat(user2Active).isEqualTo(1);
    }

    // T-LOGOUT-04: 만료된 refresh 본문 → 401 TOKEN_EXPIRED
    @Test
    void T_LOGOUT_04() throws Exception {
        Map<String, Object> tokens = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String accessToken = (String) tokens.get("accessToken");

        String expired = buildExpiredRefreshToken(999L);
        MvcResult res = logout(accessToken, expired);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("TOKEN_EXPIRED");
    }

    // T-LOGOUT-05: DB에 없는 refresh → 401 INVALID_TOKEN
    @Test
    void T_LOGOUT_05() throws Exception {
        Map<String, Object> tokens = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String accessToken = (String) tokens.get("accessToken");

        // 발급된 refresh 삭제 후 재사용
        refreshTokenRepository.deleteAll();
        String orphan = (String) tokens.get("refreshToken");

        MvcResult res = logout(accessToken, orphan);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_TOKEN");
    }

    // T-LOGOUT-06: 이미 revokedAt 설정된 refresh → 200 (idempotent)
    @Test
    void T_LOGOUT_06() throws Exception {
        Map<String, Object> tokens = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String accessToken  = (String) tokens.get("accessToken");
        String refreshToken = (String) tokens.get("refreshToken");

        // 1차 로그아웃
        logout(accessToken, refreshToken);

        // 2차 로그아웃 (이미 revoked) → idempotent 200
        MvcResult res = logout(accessToken, refreshToken);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        // revokedAt 변화 없음 (1건만 존재, 여전히 revoked)
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NOT NULL",
                Long.class, HashUtils.sha256(refreshToken));
        assertThat(count).isEqualTo(1);
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────
    private MvcResult logout(String accessToken, String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))))
                .andReturn();
    }
}
