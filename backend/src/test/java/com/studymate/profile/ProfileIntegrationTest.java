package com.studymate.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studymate.auth.repository.EmailVerificationRepository;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.auth.util.HashUtils;
import com.studymate.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
class ProfileIntegrationTest {

    private static final String TEST_EMAIL = "profile@university.ac.kr";
    private static final String TEST_PASSWORD = "Password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationRepository emailVerificationRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        emailVerificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getProfile_returnsCurrentUserProfile() throws Exception {
        long userId = saveUser();
        String token = jwtTokenProvider.createAccessToken(userId, TEST_EMAIL);

        MvcResult result = mockMvc.perform(get("/api/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("userId")).isEqualTo((int) userId);
        assertThat(body.get("email")).isEqualTo(TEST_EMAIL);
        assertThat(body.get("name")).isEqualTo("테스터");
        assertThat(body.get("school")).isEqualTo("Ajou University");
        assertThat(body.get("major")).isEqualTo("Computer Science");
        assertThat((List<?>) body.get("interestTags")).containsExactly("Algorithms", "React");
    }

    @Test
    void putProfile_updatesCurrentUserProfile() throws Exception {
        long userId = saveUser();
        String token = jwtTokenProvider.createAccessToken(userId, TEST_EMAIL);

        Map<String, Object> request = Map.of(
                "name", "새 이름",
                "school", "New University",
                "major", "AI",
                "bio", "Updated profile",
                "interestTags", List.of("AI", "Backend", "AI")
        );

        MvcResult result = mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("name")).isEqualTo("새 이름");
        assertThat((List<?>) body.get("interestTags")).containsExactly("AI", "Backend");

        Map<String, Object> saved = jdbcTemplate.queryForMap(
                "SELECT name, school, major, bio, interest_tags FROM app_user WHERE id = ?", userId);
        assertThat(saved.get("name")).isEqualTo("새 이름");
        assertThat(saved.get("school")).isEqualTo("New University");
        assertThat(saved.get("major")).isEqualTo("AI");
        assertThat(saved.get("bio")).isEqualTo("Updated profile");
        assertThat(saved.get("interest_tags")).isEqualTo("AI,Backend");
    }

    @Test
    void postProfile_savesCurrentUserProfile() throws Exception {
        long userId = saveUser();
        String token = jwtTokenProvider.createAccessToken(userId, TEST_EMAIL);

        Map<String, Object> request = Map.of(
                "name", "생성 저장",
                "school", "Ajou University",
                "major", "Design",
                "bio", "",
                "interestTags", List.of("UX", "Research")
        );

        MvcResult result = mockMvc.perform(post("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("name")).isEqualTo("생성 저장");
        assertThat(body.get("bio")).isNull();
        assertThat((List<?>) body.get("interestTags")).containsExactly("UX", "Research");
    }

    @Test
    void deleteProfile_softDeletesUserAndBlocksOldToken() throws Exception {
        long userId = saveUser();
        String token = jwtTokenProvider.createAccessToken(userId, TEST_EMAIL);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        jdbcTemplate.update(
                "INSERT INTO refresh_token (user_id, token_hash, expires_at, created_at) VALUES (?,?,?,?)",
                userId,
                HashUtils.sha256(refreshToken),
                Timestamp.from(Instant.now().plusSeconds(3600)),
                Timestamp.from(Instant.now()));

        mockMvc.perform(delete("/api/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM app_user WHERE id = ?", Boolean.class, userId);
        assertThat(deleted).isTrue();

        mockMvc.perform(get("/api/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        Long activeRefreshCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Long.class,
                userId);
        assertThat(activeRefreshCount).isZero();
    }

    @Test
    void updateProfile_rejectsBlankName() throws Exception {
        long userId = saveUser();
        String token = jwtTokenProvider.createAccessToken(userId, TEST_EMAIL);

        Map<String, Object> request = Map.of(
                "name", "",
                "school", "Ajou University",
                "major", "AI",
                "bio", "bio",
                "interestTags", List.of("AI")
        );

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private long saveUser() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        jdbcTemplate.update("""
                INSERT INTO app_user
                (email, password_hash, name, school, major, bio, interest_tags, is_email_verified, is_deleted, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                TEST_EMAIL,
                encoder.encode(TEST_PASSWORD),
                "테스터",
                "Ajou University",
                "Computer Science",
                "Original profile",
                "Algorithms,React",
                true,
                false,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, TEST_EMAIL);
    }
}
