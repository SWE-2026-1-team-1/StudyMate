package com.studymate.application.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studymate.application.controller.MyPageApplicationController;
import com.studymate.application.controller.StudyApplicationController;
import com.studymate.application.controller.TeamApplicationController;
import com.studymate.application.domain.ApplicationStatus;
import com.studymate.application.dto.response.*;
import com.studymate.application.service.ApplicationService;
import com.studymate.auth.security.CustomUserDetails;
import com.studymate.auth.security.CustomUserDetailsService;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.study.domain.StudyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        StudyApplicationController.class,
        TeamApplicationController.class,
        MyPageApplicationController.class
})
@ActiveProfiles("test")
class ApplicationRequestValidationTest {

    private static final CustomUserDetails TEST_USER = new CustomUserDetails(1L, "test@test.com");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ApplicationService applicationService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static final ApplicationCreateResponse DUMMY_CREATE =
            new ApplicationCreateResponse(1L, 1L, ApplicationStatus.PENDING, LocalDateTime.now());
    private static final ApplicationRejectResponse DUMMY_REJECT =
            new ApplicationRejectResponse(1L, ApplicationStatus.REJECTED, LocalDateTime.now(), null);
    private static final ApplicationListResponse DUMMY_LIST =
            new ApplicationListResponse(List.of(), 0L, 0, 20);
    private static final MyApplicationListResponse DUMMY_MYLIST =
            new MyApplicationListResponse(List.of(), 0L, 0, 20);

    // ─── POST /api/studies/{id}/applications ──────────────────────

    @Test
    void apply_message_길이_경계() throws Exception {
        when(applicationService.apply(anyLong(), anyLong(), any())).thenReturn(DUMMY_CREATE);

        // 500 자 통과
        postApply("{\"message\":\"" + "a".repeat(500) + "\"}").andExpect(status().isCreated());
        // 501 자 거부
        postApply("{\"message\":\"" + "a".repeat(501) + "\"}").andExpect(status().isBadRequest());
        // 빈 객체 통과
        postApply("{}").andExpect(status().isCreated());
    }

    @Test
    void apply_본문_생략_시_400() throws Exception {
        mockMvc.perform(post("/api/studies/1/applications").with(csrf()).with(user(TEST_USER)))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/teams/{id}/applications/{aid}/reject ──────────────────────

    @Test
    void reject_사유_길이_경계() throws Exception {
        when(applicationService.reject(anyLong(), anyLong(), anyLong(), any())).thenReturn(DUMMY_REJECT);

        postReject("{\"rejectReason\":\"" + "a".repeat(500) + "\"}").andExpect(status().isOk());
        postReject("{\"rejectReason\":\"" + "a".repeat(501) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void reject_본문_생략_OK() throws Exception {
        when(applicationService.reject(anyLong(), anyLong(), anyLong(), any())).thenReturn(DUMMY_REJECT);
        mockMvc.perform(post("/api/teams/1/applications/1/reject").with(csrf()).with(user(TEST_USER)))
                .andExpect(status().isOk());
    }

    // ─── GET /api/teams/{id}/applications 페이징 ──────────────────────

    @Test
    void list_페이징_파라미터_범위() throws Exception {
        when(applicationService.list(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(DUMMY_LIST);

        mockMvc.perform(get("/api/teams/1/applications").with(user(TEST_USER)).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/teams/1/applications").with(user(TEST_USER)).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/teams/1/applications").with(user(TEST_USER)).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/mypage/applications 페이징 ──────────────────────

    @Test
    void mylist_페이징_파라미터_범위() throws Exception {
        when(applicationService.listMine(anyLong(), anyInt(), anyInt())).thenReturn(DUMMY_MYLIST);

        mockMvc.perform(get("/api/mypage/applications").with(user(TEST_USER)).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions postApply(String body) throws Exception {
        return mockMvc.perform(post("/api/studies/1/applications").with(csrf()).with(user(TEST_USER))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions postReject(String body) throws Exception {
        return mockMvc.perform(post("/api/teams/1/applications/1/reject").with(csrf()).with(user(TEST_USER))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
