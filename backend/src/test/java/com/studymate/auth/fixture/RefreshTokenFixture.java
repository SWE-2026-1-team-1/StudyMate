package com.studymate.auth.fixture;

import com.studymate.auth.domain.RefreshToken;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.util.HashUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class RefreshTokenFixture {

    private RefreshTokenFixture() {}

    public static RefreshToken save(RefreshTokenRepository repo,
                                    long userId,
                                    String rawToken,
                                    Instant expiresAt) {
        return repo.save(RefreshToken.create(userId, HashUtils.sha256(rawToken), expiresAt));
    }

    // - 14일 만료 기본 헬퍼
    public static RefreshToken saveActive(RefreshTokenRepository repo,
                                          long userId,
                                          String rawToken) {
        return save(repo, userId, rawToken, Instant.now().plus(14, ChronoUnit.DAYS));
    }
}
