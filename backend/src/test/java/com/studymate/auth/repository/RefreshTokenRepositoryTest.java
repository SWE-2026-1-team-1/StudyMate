package com.studymate.auth.repository;

import com.studymate.auth.domain.RefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-RFRSH-08: revokeById — 이미 revoked row → affected = 0
 * T-RFRSH-09: revokeAllByUserId — 해당 user 의 활성 row 만 revokedAt 설정
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Autowired RefreshTokenRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final long USER_A = 1L;
    private static final long USER_B = 2L;

    // T-RFRSH-08: 이미 revoked 된 row 에 revokeById → affected = 0
    @Test
    void T_RFRSH_08() {
        Instant now = Instant.now();

        // 이미 revoked 된 row 저장
        RefreshToken rt = RefreshToken.create(USER_A, "hash-already-revoked",
                now.plus(14, ChronoUnit.DAYS));
        rt.revoke(now.minusSeconds(10));  // revokedAt 설정
        RefreshToken saved = repository.save(rt);

        // revokeById → affected = 0 (이미 revoked)
        int affected = repository.revokeById(saved.getId(), now);
        assertThat(affected).isZero();
    }

    // T-RFRSH-09: revokeAllByUserId — USER_A 활성 row 만 폐기, USER_B 영향 없음
    @Test
    void T_RFRSH_09() {
        Instant now = Instant.now();
        Instant futureExpiry = now.plus(14, ChronoUnit.DAYS);

        // USER_A 활성 row 2건
        repository.save(RefreshToken.create(USER_A, "hash-a1", futureExpiry));
        repository.save(RefreshToken.create(USER_A, "hash-a2", futureExpiry));

        // USER_A 이미 폐기된 row 1건
        RefreshToken revokedA = RefreshToken.create(USER_A, "hash-a3", futureExpiry);
        revokedA.revoke(now.minusSeconds(100));
        repository.save(revokedA);

        // USER_B 활성 row 1건
        repository.save(RefreshToken.create(USER_B, "hash-b1", futureExpiry));

        // revokeAllByUserId(USER_A) → 활성 2건만 폐기
        int affected = repository.revokeAllByUserId(USER_A, now);
        assertThat(affected).isEqualTo(2);

        // @Modifying 벌크 업데이트는 JPA 1차 캐시를 우회하므로 JdbcTemplate 으로 직접 검증
        Integer activeA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, USER_A);
        assertThat(activeA).isZero();

        // USER_B 영향 없음
        Integer activeB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, USER_B);
        assertThat(activeB).isEqualTo(1);
    }
}
