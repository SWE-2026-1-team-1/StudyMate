package com.studymate.auth.integration;

import com.studymate.auth.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthTokenRefreshIntegrationTest extends AuthIntegrationTestBase {

    // T-RFRSH-01: 정상 rotation → 200, 신규 refresh, 이전 row revokedAt != null, 신규 row 추가
    @Test
    void T_RFRSH_01() throws Exception {
        Map<String, Object> tokens = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String oldRefresh = (String) tokens.get("refreshToken");

        MvcResult res = refreshToken(oldRefresh);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        String newRefresh = (String) body.get("refreshToken");
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // 이전 row revoked
        Long revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NOT NULL",
                Long.class, HashUtils.sha256(oldRefresh));
        assertThat(revokedCount).isEqualTo(1);

        // 신규 row 추가
        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NULL",
                Long.class, HashUtils.sha256(newRefresh));
        assertThat(activeCount).isEqualTo(1);
    }

    // T-RFRSH-02: 회전된 이전 refresh 재사용 → 401 INVALID_TOKEN, 모든 활성 refresh 폐기
    @Test
    void T_RFRSH_02() throws Exception {
        Map<String, Object> tokens = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        String oldRefresh = (String) tokens.get("refreshToken");

        // 1차 rotation → oldRefresh 폐기, newRefresh 발급
        MvcResult r1 = refreshToken(oldRefresh);
        assertThat(r1.getResponse().getStatus()).isEqualTo(200);
        Map<?, ?> body1 = objectMapper.readValue(r1.getResponse().getContentAsString(), Map.class);
        String newRefresh = (String) body1.get("refreshToken");

        // oldRefresh 재사용 → 재사용 감지 → 전체 폐기
        MvcResult r2 = refreshToken(oldRefresh);
        assertThat(r2.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(r2)).isEqualTo("INVALID_TOKEN");

        // newRefresh 도 폐기됐는지 확인
        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NULL",
                Long.class, HashUtils.sha256(newRefresh));
        assertThat(activeCount).isZero();
    }

    // T-RFRSH-03: 만료된 refresh → 401 TOKEN_EXPIRED, DB 변화 없음
    @Test
    void T_RFRSH_03() throws Exception {
        // userId 0 은 DB에 없어도 JWT 파싱 단계에서 만료 감지됨
        String expired = buildExpiredRefreshToken(999L);
        long before = refreshTokenRepository.count();

        MvcResult res = refreshToken(expired);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("TOKEN_EXPIRED");
        assertThat(refreshTokenRepository.count()).isEqualTo(before);
    }

    // T-RFRSH-04: DB에 없는 (서명 valid) refresh → 401 INVALID_TOKEN
    @Test
    void T_RFRSH_04() throws Exception {
        Map<String, Object> tokens = doFullLogin(TEST_EMAIL, TEST_PASSWORD);
        // 발급된 토큰을 DB에서 삭제 후 재사용
        refreshTokenRepository.deleteAll();
        String orphanRefresh = (String) tokens.get("refreshToken");

        MvcResult res = refreshToken(orphanRefresh);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_TOKEN");
    }

    // T-RFRSH-05: 잘못된 서명 → 401 INVALID_TOKEN
    @Test
    void T_RFRSH_05() throws Exception {
        String tampered = buildWrongSignatureToken(999L);

        MvcResult res = refreshToken(tampered);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_TOKEN");
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────
    private MvcResult refreshToken(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", token))))
                .andReturn();
    }
}
