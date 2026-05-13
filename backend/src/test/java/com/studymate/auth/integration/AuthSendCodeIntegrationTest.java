package com.studymate.auth.integration;

import com.studymate.auth.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSendCodeIntegrationTest extends AuthIntegrationTestBase {

    // T-SEND-01: 신규 이메일 → 200, EV row 1건 생성, 6자리 코드 메일 발송
    @Test
    void T_SEND_01() throws Exception {
        mockMvc.perform(post("/api/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", TEST_EMAIL))))
                .andExpect(status().isOk());

        // 상태 어서션
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM email_verification WHERE email = ?", TEST_EMAIL);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);

        String codeHash = (String) row.get("code_hash");
        assertThat(codeHash).hasSize(64);                  // SHA-256 hex = 64자
        assertThat(row.get("verified_at")).isNull();
        assertThat(row.get("consumed_at")).isNull();
        assertThat(row.get("attempt_count")).isEqualTo(0);

        // expires_at ≈ now + 10분 (±5초 허용) — H2 는 OffsetDateTime 반환 가능
        Instant expiresAt = toInstant(row.get("expires_at"));
        assertThat(expiresAt).isBetween(
                Instant.now().plus(9, ChronoUnit.MINUTES).plusSeconds(55),
                Instant.now().plus(10, ChronoUnit.MINUTES).plusSeconds(5));

        // 상호작용 어서션
        assertThat(fakeEmailSender.sendCount()).isEqualTo(1);
        assertThat(fakeEmailSender.lastTo()).isEqualTo(TEST_EMAIL);

        String sentCode = fakeEmailSender.lastCode();
        assertThat(sentCode).matches("\\d{6}");           // 6자리 숫자
        assertThat(HashUtils.sha256(sentCode)).isEqualTo(codeHash); // 해시 일치
    }

    // T-SEND-05: 동일 이메일 이미 가입 → 409 CONFLICT, EV row 미생성, 메일 미발송
    @Test
    void T_SEND_05() throws Exception {
        saveUser(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", TEST_EMAIL))))
                .andExpect(status().isConflict());

        assertThat(emailVerificationRepository.count()).isZero();
        assertThat(fakeEmailSender.sendCount()).isZero();
    }

    // T-SEND-06: 쿨다운 60초 미경과 (30초 전 발송) → 400 INVALID_INPUT "잠시 후 다시 시도해주세요"
    @Test
    void T_SEND_06() throws Exception {
        // 1차 발송
        sendCode(TEST_EMAIL);
        assertThat(fakeEmailSender.sendCount()).isEqualTo(1);

        // created_at 을 30초 전으로 조작
        jdbcTemplate.update(
                "UPDATE email_verification SET created_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minus(30, ChronoUnit.SECONDS)), TEST_EMAIL);

        // 2차 발송 시도 → 쿨다운 (60초 미경과)
        MvcResult res = mockMvc.perform(post("/api/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", TEST_EMAIL))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(errorCode(res)).isEqualTo("INVALID_INPUT");
        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("message")).isEqualTo("잠시 후 다시 시도해주세요");

        // 신규 row 미생성, 메일 미발송 (1회만)
        assertThat(emailVerificationRepository.count()).isEqualTo(1);
        assertThat(fakeEmailSender.sendCount()).isEqualTo(1);
    }

    // T-SEND-07: 쿨다운 60초 경과 (61초 전 발송) → 200, row 2건, 메일 2회 발송
    @Test
    void T_SEND_07() throws Exception {
        // 1차 발송
        sendCode(TEST_EMAIL);

        // created_at 을 61초 전으로 조작
        jdbcTemplate.update(
                "UPDATE email_verification SET created_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minus(61, ChronoUnit.SECONDS)), TEST_EMAIL);

        // 2차 발송 → 쿨다운 경과
        mockMvc.perform(post("/api/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", TEST_EMAIL))))
                .andExpect(status().isOk());

        assertThat(emailVerificationRepository.count()).isEqualTo(2);
        assertThat(fakeEmailSender.sendCount()).isEqualTo(2);
    }
}
