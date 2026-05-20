package com.studymate.study.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.config.TestConfig;
import com.studymate.study.repository.*;
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
public abstract class StudyIntegrationTestBase {

    protected static final String TEST_EMAIL    = "leader@university.ac.kr";
    protected static final String TEST_EMAIL2   = "other@university.ac.kr";
    protected static final String TEST_PASSWORD = "Password1";
    protected static final String TEST_NAME     = "리더";
    protected static final String TEST_NAME2    = "비리더";

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
        studyLanguageRepository.deleteAll();
        studyTagRepository.deleteAll();
        studyMemberRepository.deleteAll();
        studyRepository.deleteAll();
        tagRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ─── 헬퍼: 사용자 생성 ───────────────────────────────────────────────
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

    /** access token 발급 (실제 JwtTokenProvider 사용). */
    protected String bearerToken(long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId, "");
    }

    // ─── 헬퍼: API 호출 ─────────────────────────────────────────────────
    protected MvcResult postStudy(String token, Object body) throws Exception {
        return mockMvc.perform(post("/api/studies")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
                .andReturn();
    }

    protected MvcResult getStudies(String token, int page, int size) throws Exception {
        return mockMvc.perform(get("/api/studies")
                .header("Authorization", token)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size)))
                .andReturn();
    }

    protected MvcResult getStudies(String token) throws Exception {
        return mockMvc.perform(get("/api/studies")
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult getStudy(String token, long studyId) throws Exception {
        return mockMvc.perform(get("/api/studies/" + studyId)
                .header("Authorization", token))
                .andReturn();
    }

    protected MvcResult patchStudy(String token, long studyId, Object body) throws Exception {
        return mockMvc.perform(patch("/api/studies/" + studyId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
                .andReturn();
    }

    protected MvcResult deleteStudy(String token, long studyId) throws Exception {
        return mockMvc.perform(delete("/api/studies/" + studyId)
                .header("Authorization", token))
                .andReturn();
    }

    // ─── 헬퍼: JSON ────────────────────────────────────────────────────
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

    /** JPA 로 study 조회 (캐시 없는 새 트랜잭션). */
    protected com.studymate.study.domain.Study queryStudy(long studyId) {
        return studyRepository.findById(studyId)
                .orElseThrow(() -> new RuntimeException("study not found: " + studyId));
    }

    /** is_deleted 포함 상태 확인용 JdbcTemplate (H2 캐시 우회). */
    protected int queryIsDeleted(long studyId) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM study WHERE id = ?", Integer.class, studyId);
        return v == null ? -1 : v;
    }

    protected String queryTitle(long studyId) {
        return jdbcTemplate.queryForObject("SELECT title FROM study WHERE id = ?", String.class, studyId);
    }

    protected String queryStatus(long studyId) {
        return jdbcTemplate.queryForObject("SELECT status FROM study WHERE id = ?", String.class, studyId);
    }
}
