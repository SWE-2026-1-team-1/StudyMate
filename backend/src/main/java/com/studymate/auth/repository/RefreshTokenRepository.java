package com.studymate.auth.repository;

import com.studymate.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // - 재사용 감지 시 해당 user 의 모든 활성 refresh 일괄 폐기 (D-007)
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
    int revokeAllByUserId(@Param("userId") long userId, @Param("now") Instant now);

    // - 단건 폐기 (logout / rotation); affected=0 → 이미 revoked
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.id = :id and r.revokedAt is null")
    int revokeById(@Param("id") long id, @Param("now") Instant now);
}
