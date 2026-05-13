package com.studymate.auth.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studymate.auth.dto.response.SignupResponse;
import com.studymate.auth.security.CustomUserDetailsService;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.auth.service.AuthService;
import com.studymate.auth.service.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §5 입력 유효성 매트릭스 — @WebMvcTest 슬라이스.
 * @WithMockUser + csrf() 로 Spring Security 의 기본 보호를 우회하고 Bean Validation 레이어만 검증.
 */
@WebMvcTest(controllers = com.studymate.auth.controller.AuthController.class)
@ActiveProfiles("test")
@WithMockUser
class AuthRequestValidationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean EmailVerificationService emailVerificationService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    // ═══════════════════════════════════════════════════════════════════
    // T-SEND-02~04: SendEmailCodeRequest 검증
    // ═══════════════════════════════════════════════════════════════════

    // T-SEND-02: 이메일 형식 아님 → 400 INVALID_INPUT
    @Test
    void T_SEND_02() throws Exception {
        mockMvc.perform(post("/api/auth/email/send-code").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-SEND-03: 학교 도메인 외 → 400 INVALID_INPUT (@Pattern)
    @Test
    void T_SEND_03() throws Exception {
        mockMvc.perform(post("/api/auth/email/send-code").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@gmail.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-SEND-04: email 필드 누락 → 400 INVALID_INPUT
    @Test
    void T_SEND_04() throws Exception {
        mockMvc.perform(post("/api/auth/email/send-code").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // T-VRFY-07~08: VerifyEmailCodeRequest 검증
    // ═══════════════════════════════════════════════════════════════════

    // T-VRFY-07a: code 5자리 → 400
    @Test
    void T_VRFY_07_short() throws Exception {
        mockMvc.perform(post("/api/auth/email/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@university.ac.kr", "code", "12345"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-VRFY-07b: code 7자리 → 400
    @Test
    void T_VRFY_07_long() throws Exception {
        mockMvc.perform(post("/api/auth/email/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@university.ac.kr", "code", "1234567"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-VRFY-08a: email 누락 → 400
    @Test
    void T_VRFY_08_email_missing() throws Exception {
        mockMvc.perform(post("/api/auth/email/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-VRFY-08b: code 누락 → 400
    @Test
    void T_VRFY_08_code_missing() throws Exception {
        mockMvc.perform(post("/api/auth/email/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@university.ac.kr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // T-SIGN-07~12: SignupRequest.password 검증
    // ═══════════════════════════════════════════════════════════════════

    // T-SIGN-07: 7자 (영문+숫자) → 400
    @Test
    void T_SIGN_07() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@test.com", "password", "abc1234", "name", "N"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-SIGN-08: 8자 숫자 없음 → 400
    @Test
    void T_SIGN_08() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@test.com", "password", "abcdefgh", "name", "N"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-SIGN-09: 8자 영문 없음 → 400
    @Test
    void T_SIGN_09() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@test.com", "password", "12345678", "name", "N"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-SIGN-10: 8자 영문+숫자 정상 → Validation 통과 (201)
    @Test
    void T_SIGN_10() throws Exception {
        when(authService.signup(any()))
                .thenReturn(new SignupResponse(1L, "a@test.com", "N"));

        mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@test.com", "password", "abc12345", "name", "N"))))
                .andExpect(status().isCreated());
    }

    // T-SIGN-11: 21자 → 400 (8+10+3 = 21자)
    @Test
    void T_SIGN_11() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@test.com", "password", "abcdefgh1234567890abc", "name", "N"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-SIGN-12: 특수문자 포함 → 400 (현 정규식은 영문+숫자만 허용)
    @Test
    void T_SIGN_12() throws Exception {
        mockMvc.perform(post("/api/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@test.com", "password", "abc12345!", "name", "N"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // T-LOGIN-05: LoginRequest 검증
    // ═══════════════════════════════════════════════════════════════════

    // T-LOGIN-05a: email 누락 → 400
    @Test
    void T_LOGIN_05_email() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("password", "Pass1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // T-LOGIN-05b: password 누락 → 400
    @Test
    void T_LOGIN_05_password() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // T-RFRSH-06: RefreshTokenRequest 검증
    // ═══════════════════════════════════════════════════════════════════

    // T-RFRSH-06: refreshToken 누락 → 400
    @Test
    void T_RFRSH_06() throws Exception {
        mockMvc.perform(post("/api/auth/token/refresh").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // T-LOGOUT-07: logout body 누락 → 400 (@Valid 이 method body 실행 전 발동)
    // ═══════════════════════════════════════════════════════════════════

    // T-LOGOUT-07: refreshToken 본문 누락 → 400
    @Test
    void T_LOGOUT_07() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
