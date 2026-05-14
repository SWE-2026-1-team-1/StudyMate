package com.studymate.auth.repository;

import com.studymate.auth.domain.EmailVerification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-VRFY-09: findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc 쿼리 검증.
 * consumed row 제외, 가장 최신 미소비 row 반환.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailVerificationRepositoryTest {

    @Autowired EmailVerificationRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String EMAIL = "repo-test@university.ac.kr";

    // T-VRFY-09: consumed row 는 제외, 가장 최신 미소비 row 반환
    @Test
    void T_VRFY_09() {
        Instant now = Instant.now();

        // Row A: 오래됨 + consumed
        jdbcTemplate.update(
                "INSERT INTO email_verification (email, code_hash, expires_at, verified_at, consumed_at, attempt_count, created_at) VALUES (?,?,?,?,?,?,?)",
                EMAIL, "hash-a",
                Timestamp.from(now.plus(10, ChronoUnit.MINUTES)),
                Timestamp.from(now.minusSeconds(60)),
                Timestamp.from(now.minusSeconds(30)),  // consumed
                0,
                Timestamp.from(now.minus(2, ChronoUnit.HOURS)));

        // Row B: 최신 + 미소비
        jdbcTemplate.update(
                "INSERT INTO email_verification (email, code_hash, expires_at, verified_at, consumed_at, attempt_count, created_at) VALUES (?,?,?,?,?,?,?)",
                EMAIL, "hash-b",
                Timestamp.from(now.plus(10, ChronoUnit.MINUTES)),
                null, null, 0,
                Timestamp.from(now.minus(1, ChronoUnit.MINUTES)));  // 최신

        // Row C: 더 오래됨 + 미소비
        jdbcTemplate.update(
                "INSERT INTO email_verification (email, code_hash, expires_at, verified_at, consumed_at, attempt_count, created_at) VALUES (?,?,?,?,?,?,?)",
                EMAIL, "hash-c",
                Timestamp.from(now.plus(10, ChronoUnit.MINUTES)),
                null, null, 0,
                Timestamp.from(now.minus(3, ChronoUnit.HOURS)));

        // When
        Optional<EmailVerification> result = repository
                .findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(EMAIL);

        // Then: Row B (최신 미소비) 반환, Row A (consumed) 제외
        assertThat(result).isPresent();
        assertThat(result.get().getCodeHash()).isEqualTo("hash-b");
    }
}
