package com.studymate.study.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class MemberIntegrationTestBase {

    protected static final String LEADER_EMAIL  = "leader@university.ac.kr";
    protected static final String MEMBER_EMAIL  = "member@university.ac.kr";
    protected static final String MEMBER2_EMAIL = "member2@university.ac.kr";
    protected static final String OUTSIDER_EMAIL= "outsider@university.ac.kr";
    protected static final String TEST_PASSWORD = "Password1";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RefreshTokenRepository refreshTokenRepository;
    @Autowired protected StudyRepository studyRepository;
    @Autowired protected StudyMemberRepository studyMemberRepository;
    @Autowired protected StudyTagRepository studyTagRepository;
    @Autowired protected StudyLanguageRepository studyLanguageRepository;
    @Autowired protected TagRepository tagRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM application");
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

    protected long createOpenStudy(long leaderId, int maxMembers) {
        Study study = studyRepository.save(Study.create("스터디", "설명", maxMembers, 4, "주1회"));
        long studyId = study.getId();
        studyMemberRepository.save(StudyMember.createLeader(studyId, leaderId));
        return studyId;
    }

    protected long addMember(long studyId, long userId) {
        return studyMemberRepository.save(StudyMember.createMember(studyId, userId)).getId();
    }

    protected long leaderMemberId(long studyId) {
        return studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();
    }

    protected void markStudyClosed(long studyId, int currentMembers) {
        jdbcTemplate.update("UPDATE study SET status='CLOSED', current_member_count=? WHERE id=?",
                currentMembers, studyId);
    }

    protected void markStudyDeleted(long studyId) {
        jdbcTemplate.update("UPDATE study SET is_deleted=1 WHERE id=?", studyId);
    }

    // ─── API 호출 헬퍼 ─────────────────────────────────────────────────

    protected MvcResult getMembers(String token, long teamId) throws Exception {
        return mockMvc.perform(get("/api/teams/" + teamId + "/members")
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult getMembersUnauth(long teamId) throws Exception {
        return mockMvc.perform(get("/api/teams/" + teamId + "/members")).andReturn();
    }

    protected MvcResult deleteMember(String token, long teamId, long memberId) throws Exception {
        return mockMvc.perform(delete("/api/teams/" + teamId + "/members/" + memberId)
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult deleteMemberUnauth(long teamId, long memberId) throws Exception {
        return mockMvc.perform(delete("/api/teams/" + teamId + "/members/" + memberId)).andReturn();
    }

    protected MvcResult deleteSelf(String token, long teamId) throws Exception {
        return mockMvc.perform(delete("/api/teams/" + teamId + "/members/me")
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult deleteSelfUnauth(long teamId) throws Exception {
        return mockMvc.perform(delete("/api/teams/" + teamId + "/members/me")).andReturn();
    }

    // ─── JSON / 조회 ─────────────────────────────────────────────────

    protected String errorCode(MvcResult res) throws Exception {
        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return (String) body.get("code");
    }

    protected String errorMessage(MvcResult res) throws Exception {
        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return (String) body.get("message");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> responseBody(MvcResult res) throws Exception {
        return objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
    }

    protected Study queryStudy(long studyId) {
        return studyRepository.findById(studyId).orElseThrow();
    }

    protected StudyMember queryMember(long memberId) {
        return studyMemberRepository.findById(memberId).orElseThrow();
    }
}
