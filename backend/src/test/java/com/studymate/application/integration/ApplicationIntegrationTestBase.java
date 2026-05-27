package com.studymate.application.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studymate.application.repository.ApplicationRepository;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.config.TestConfig;
import com.studymate.study.domain.Study;
import com.studymate.study.domain.StudyMember;
import com.studymate.study.repository.StudyLanguageRepository;
import com.studymate.study.repository.StudyMemberRepository;
import com.studymate.study.repository.StudyRepository;
import com.studymate.study.repository.StudyTagRepository;
import com.studymate.study.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class ApplicationIntegrationTestBase {

    protected static final String LEADER_EMAIL    = "leader@university.ac.kr";
    protected static final String APPLICANT_EMAIL = "applicant@university.ac.kr";
    protected static final String OTHER_EMAIL     = "other@university.ac.kr";
    protected static final String TEST_PASSWORD   = "Password1";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RefreshTokenRepository refreshTokenRepository;
    @Autowired protected StudyRepository studyRepository;
    @Autowired protected StudyMemberRepository studyMemberRepository;
    @Autowired protected StudyTagRepository studyTagRepository;
    @Autowired protected StudyLanguageRepository studyLanguageRepository;
    @Autowired protected TagRepository tagRepository;
    @Autowired protected ApplicationRepository applicationRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanUp() {
        applicationRepository.deleteAll();
        studyLanguageRepository.deleteAll();
        studyTagRepository.deleteAll();
        studyMemberRepository.deleteAll();
        studyRepository.deleteAll();
        tagRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        seedStudyRoles();
    }

    private void seedStudyRoles() {
        // ddl-auto: create-drop 으로 매번 새 테이블. 권한 검증용 LEADER / MEMBER row 시드.
        Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM study_role", Integer.class);
        if (cnt != null && cnt > 0) return;
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
            INSERT INTO study_role
              (code, name, sort_order, can_approve_application, can_manage_member,
               can_create_attendance, can_post_notice, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
        """, "LEADER", "조장", 1, true, true, true, true, now, now);
        jdbcTemplate.update("""
            INSERT INTO study_role
              (code, name, sort_order, can_approve_application, can_manage_member,
               can_create_attendance, can_post_notice, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
        """, "MEMBER", "일반 멤버", 9, false, false, false, false, now, now);
    }

    protected long saveUser(String email, String name) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(4);
        jdbcTemplate.update(
                "INSERT INTO app_user (email, password_hash, name, is_email_verified, is_deleted, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                email, enc.encode(TEST_PASSWORD), name,
                true, false,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", Long.class, email);
    }

    protected String bearerToken(long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId, "");
    }

    // ─── API 호출 헬퍼 ─────────────────────────────────────────────────

    protected MvcResult postApply(String token, long studyId, Object body) throws Exception {
        return mockMvc.perform(post("/api/studies/" + studyId + "/applications")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
                .andReturn();
    }

    protected MvcResult deleteApplyMine(String token, long studyId) throws Exception {
        return mockMvc.perform(delete("/api/studies/" + studyId + "/applications/my")
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult getTeamApplications(String token, long teamId, int page, int size) throws Exception {
        return mockMvc.perform(get("/api/teams/" + teamId + "/applications")
                .header("Authorization", token)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size)))
                .andReturn();
    }

    protected MvcResult getTeamApplications(String token, long teamId) throws Exception {
        return mockMvc.perform(get("/api/teams/" + teamId + "/applications")
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult postApprove(String token, long teamId, long applicationId) throws Exception {
        return mockMvc.perform(post("/api/teams/" + teamId + "/applications/" + applicationId + "/approve")
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult postReject(String token, long teamId, long applicationId, Object body) throws Exception {
        var req = post("/api/teams/" + teamId + "/applications/" + applicationId + "/reject")
                .header("Authorization", token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult getMyApplications(String token) throws Exception {
        return mockMvc.perform(get("/api/mypage/applications")
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult getMyApplications(String token, int page, int size) throws Exception {
        return mockMvc.perform(get("/api/mypage/applications")
                .header("Authorization", token)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size)))
                .andReturn();
    }

    // ─── JSON 헬퍼 ─────────────────────────────────────────────────

    protected String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    protected String errorCode(MvcResult res) throws Exception {
        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return (String) body.get("code");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> responseBody(MvcResult res) throws Exception {
        return objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
    }

    // ─── DB 조회 헬퍼 ─────────────────────────────────────────────────

    protected Study queryStudy(long studyId) {
        return studyRepository.findById(studyId).orElseThrow();
    }

    protected String queryApplicationStatus(long applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM application WHERE id=?", String.class, applicationId);
    }

    protected boolean applicationExists(long applicationId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application WHERE id=?", Integer.class, applicationId);
        return cnt != null && cnt > 0;
    }

    /** 활성 멤버 row 존재 여부 (LEADER 포함). */
    protected boolean activeMemberExists(long studyId, long userId) {
        return studyMemberRepository.existsByStudyIdAndUserIdAndIsActiveTrue(studyId, userId);
    }
}
