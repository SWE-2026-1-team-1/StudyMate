package com.studymate.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studymate.auth.fixture.FakeEmailSender;
import com.studymate.auth.repository.EmailVerificationRepository;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.config.TestConfig;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class AuthIntegrationTestBase {

    protected static final String TEST_EMAIL    = "test@university.ac.kr";
    protected static final String TEST_PASSWORD = "Password1";
    protected static final String TEST_NAME     = "테스터";
    protected static final String TEST_SECRET   =
            "test-secret-key-for-testing-purposes-at-least-32-chars";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected EmailVerificationRepository emailVerificationRepository;
    @Autowired protected RefreshTokenRepository refreshTokenRepository;
    @Autowired protected FakeEmailSender fakeEmailSender;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanUp() {
        // FK 순서: RefreshToken → User / EmailVerification
        refreshTokenRepository.deleteAll();
        emailVerificationRepository.deleteAll();
        userRepository.deleteAll();
        fakeEmailSender.clear();
    }

    // ─── 헬퍼: JSON 직렬화 ───────────────────────────────────────────────
    protected String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    // ─── 헬퍼: API 호출 ─────────────────────────────────────────────────

    protected MvcResult sendCode(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))))
                .andReturn();
    }

    protected MvcResult verifyCode(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "code", code))))
                .andReturn();
    }

    protected MvcResult signup(String email, String password, String name) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password, "name", name))))
                .andReturn();
    }

    protected MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))))
                .andReturn();
    }

    // ─── 헬퍼: 완전한 가입 플로우 (verify 후 signup) ────────────────────
    protected void doFullSignup(String email, String password, String name) throws Exception {
        sendCode(email);
        String code = fakeEmailSender.lastCode();
        fakeEmailSender.clear();
        verifyCode(email, code);
        signup(email, password, name);
    }

    // ─── 헬퍼: 가입 + 로그인 후 토큰 반환 ───────────────────────────────
    protected Map<String, Object> doFullLogin(String email, String password) throws Exception {
        doFullSignup(email, password, TEST_NAME);
        fakeEmailSender.clear();
        MvcResult res = login(email, password);
        return objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
    }

    // ─── 헬퍼: 만료된 JWT 생성 ──────────────────────────────────────────
    protected String buildExpiredRefreshToken(long userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        return io.jsonwebtoken.Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(Date.from(past.minus(30, ChronoUnit.MINUTES)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();
    }

    // ─── 헬퍼: 잘못된 서명 JWT 생성 ─────────────────────────────────────
    protected String buildWrongSignatureToken(long userId) {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-for-testing-purposes-at-least-32-chars"
                        .getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return io.jsonwebtoken.Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(wrongKey)
                .compact();
    }

    // ─── 헬퍼: BCrypt 해시가 적용된 사용자 직접 저장 ─────────────────────
    protected long saveUser(String email, String rawPassword) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(12);
        jdbcTemplate.update(
                "INSERT INTO app_user (email, password_hash, name, is_email_verified, is_deleted, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                email, enc.encode(rawPassword), TEST_NAME,
                true, false,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", Long.class, email);
    }

    // ─── 헬퍼: DB에 EmailVerification row 직접 삽입 ───────────────────────
    protected void insertEv(String email, String codeHash,
                            Instant expiresAt, Instant verifiedAt,
                            Instant consumedAt, int attemptCount,
                            Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO email_verification (email, code_hash, expires_at, verified_at, consumed_at, attempt_count, created_at) VALUES (?,?,?,?,?,?,?)",
                email, codeHash,
                Timestamp.from(expiresAt),
                verifiedAt  != null ? Timestamp.from(verifiedAt)  : null,
                consumedAt  != null ? Timestamp.from(consumedAt)  : null,
                attemptCount,
                Timestamp.from(createdAt));
    }

    // ─── 헬퍼: DB 칼럼값 → Instant (H2 는 OffsetDateTime, Timestamp 모두 반환 가능) ─
    protected Instant toInstant(Object dbValue) {
        if (dbValue instanceof Timestamp ts) return ts.toInstant();
        if (dbValue instanceof OffsetDateTime odt) return odt.toInstant();
        if (dbValue instanceof java.time.LocalDateTime ldt)
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        throw new IllegalArgumentException("Unexpected type: " + dbValue.getClass());
    }

    // ─── 헬퍼: 응답 JSON 코드 추출 ──────────────────────────────────────
    protected String errorCode(MvcResult res) throws Exception {
        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return (String) body.get("code");
    }
}
