package com.studymate.study.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studymate.auth.security.CustomUserDetails;
import com.studymate.auth.security.CustomUserDetailsService;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.study.domain.StudyStatus;
import com.studymate.study.dto.response.StudyCreateResponse;
import com.studymate.study.dto.response.StudyUpdateResponse;
import com.studymate.study.service.StudyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.studymate.study.controller.StudyController.class)
@ActiveProfiles("test")
class StudyRequestValidationTest {

    private static final CustomUserDetails TEST_USER = new CustomUserDetails(1L, "test@test.com");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StudyService studyService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static final StudyCreateResponse DUMMY_CREATE =
            new StudyCreateResponse(1L, "스터디", StudyStatus.OPEN, 1, 5);
    private static final StudyUpdateResponse DUMMY_UPDATE =
            new StudyUpdateResponse(1L, "스터디", StudyStatus.OPEN, LocalDateTime.now());

    // ─── POST /api/studies ────────────────────────────────────────────

    @Test
    void T_STUDY_CREATE_05_title_길이_경계() throws Exception {
        when(studyService.create(anyLong(), any())).thenReturn(DUMMY_CREATE);

        postStudy(bodyWith("title", "a")).andExpect(status().isBadRequest());             // 1자
        postStudy(bodyWith("title", "a".repeat(51))).andExpect(status().isBadRequest()); // 51자
        postStudy(bodyWith("title", "ab")).andExpect(status().isCreated());              // 2자 경계
        postStudy(bodyWith("title", "a".repeat(50))).andExpect(status().isCreated());   // 50자 경계
    }

    @Test
    void T_STUDY_CREATE_06_description_길이() throws Exception {
        when(studyService.create(anyLong(), any())).thenReturn(DUMMY_CREATE);
        postStudy(bodyWith("description", "a".repeat(2001))).andExpect(status().isBadRequest());
        postStudy(bodyWith("description", "a".repeat(2000))).andExpect(status().isCreated());
    }

    @Test
    void T_STUDY_CREATE_07_tags_빈배열() throws Exception {
        postStudy(bodyWithList("tags", List.of())).andExpect(status().isBadRequest());
    }

    @Test
    void T_STUDY_CREATE_08_tags_11개() throws Exception {
        List<String> tags = IntStream.range(0, 11).mapToObj(i -> "tag" + i).toList();
        postStudy(bodyWithList("tags", tags)).andExpect(status().isBadRequest());
    }

    @Test
    void T_STUDY_CREATE_09_tags_원소_위반() throws Exception {
        postStudy(bodyWithList("tags", List.of(""))).andExpect(status().isBadRequest());
        postStudy(bodyWithList("tags", List.of("a".repeat(31)))).andExpect(status().isBadRequest());
    }

    @Test
    void T_STUDY_CREATE_11_maxMembers_범위() throws Exception {
        when(studyService.create(anyLong(), any())).thenReturn(DUMMY_CREATE);
        postStudy(bodyWith("maxMembers", 1)).andExpect(status().isBadRequest());
        postStudy(bodyWith("maxMembers", 51)).andExpect(status().isBadRequest());
        postStudy(bodyWith("maxMembers", 2)).andExpect(status().isCreated());
        postStudy(bodyWith("maxMembers", 50)).andExpect(status().isCreated());
    }

    @Test
    void T_STUDY_CREATE_12_durationWeeks_범위() throws Exception {
        when(studyService.create(anyLong(), any())).thenReturn(DUMMY_CREATE);
        postStudy(bodyWith("durationWeeks", 0)).andExpect(status().isBadRequest());
        postStudy(bodyWith("durationWeeks", 53)).andExpect(status().isBadRequest());
        postStudy(bodyWith("durationWeeks", 1)).andExpect(status().isCreated());
        postStudy(bodyWith("durationWeeks", 52)).andExpect(status().isCreated());
    }

    @Test
    void T_STUDY_CREATE_13_meetingCycle_길이() throws Exception {
        when(studyService.create(anyLong(), any())).thenReturn(DUMMY_CREATE);
        postStudy(bodyWith("meetingCycle", "")).andExpect(status().isBadRequest());
        postStudy(bodyWith("meetingCycle", "a".repeat(51))).andExpect(status().isBadRequest());
        postStudy(bodyWith("meetingCycle", "a".repeat(50))).andExpect(status().isCreated());
    }

    // ─── GET /api/studies ─────────────────────────────────────────────

    @Test
    void T_STUDY_LIST_06_07_page_size_범위() throws Exception {
        mockMvc.perform(get("/api/studies").with(user(TEST_USER)).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/studies").with(user(TEST_USER)).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/studies").with(user(TEST_USER)).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /api/studies/{studyId} ────────────────────────────────

    @Test
    void T_STUDY_PATCH_12_status_소문자_거부() throws Exception {
        // 소문자 → Jackson enum 매핑 실패 → HttpMessageNotReadableException → 400
        patchStudy(1L, "{\"status\": \"open\"}").andExpect(status().isBadRequest());
        patchStudy(1L, "{\"status\": \"closed\"}").andExpect(status().isBadRequest());
    }

    // T-STUDY-PATCH-21 (maxMembers 범위) / T-STUDY-PATCH-23 (tags 빈배열):
    // 서비스 레이어 검증이므로 StudyPatchIntegrationTest 에서 커버 (서비스 mock 시 불가).

    // ─── helpers ─────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions postStudy(String body) throws Exception {
        return mockMvc.perform(post("/api/studies").with(csrf()).with(user(TEST_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions patchStudy(long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/studies/" + id).with(csrf()).with(user(TEST_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String bodyWith(String key, Object value) throws Exception {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("title", "스터디");
        map.put("description", "설명");
        map.put("tags", List.of("Java"));
        map.put("languages", List.of("ko"));
        map.put("maxMembers", 5);
        map.put("durationWeeks", 4);
        map.put("meetingCycle", "매주");
        map.put(key, value);
        return objectMapper.writeValueAsString(map);
    }

    private String bodyWithList(String key, List<?> value) throws Exception {
        return bodyWith(key, value);
    }
}
