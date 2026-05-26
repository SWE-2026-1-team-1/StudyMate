package com.studymate.auth.service;

import com.studymate.auth.domain.RefreshToken;
import com.studymate.auth.domain.User;
import com.studymate.auth.exception.AuthException;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.auth.util.HashUtils;
import com.studymate.common.exception.BusinessException;
import com.studymate.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@Transactional
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public record TokenPair(String accessToken, String refreshToken) {}

    // - 로그인 시 access + refresh 토큰 쌍 발급 및 DB 저장
    public TokenPair issueTokenPair(long userId, String email) {
        String rawRefresh  = jwtTokenProvider.createRefreshToken(userId);
        String accessToken = jwtTokenProvider.createAccessToken(userId, email);
        String tokenHash   = HashUtils.sha256(rawRefresh);
        Instant expiresAt  = jwtTokenProvider.refreshExpiresAt();

        refreshTokenRepository.save(RefreshToken.create(userId, tokenHash, expiresAt));
        return new TokenPair(accessToken, rawRefresh);
    }

    // - Rotation: 독립 트랜잭션 + 재사용 감지 시 revokeAll 커밋 보장
    //   REQUIRES_NEW: 외부 @Transactional 과 격리, noRollbackFor: AuthException 시에도 커밋
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public TokenPair rotate(String refreshToken) {
        Claims claims = parseOrThrow(refreshToken);
        long userId = Long.parseLong(claims.getSubject());
        String tokenHash = HashUtils.sha256(refreshToken);

        RefreshToken rt = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_TOKEN));

        // 이미 폐기된 토큰 → 재사용 시그널 (T-RFRSH-02)
        if (rt.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllByUserId(userId, Instant.now(clock));
            throw new AuthException(ErrorCode.INVALID_TOKEN);
        }

        // 단건 폐기 (affected=0 → 동시 race → 전체 폐기, T-RFRSH-07)
        int affected = refreshTokenRepository.revokeById(rt.getId(), Instant.now(clock));
        if (affected == 0) {
            refreshTokenRepository.revokeAllByUserId(userId, Instant.now(clock));
            throw new AuthException(ErrorCode.INVALID_TOKEN);
        }

        // access token 에 email 필요 → user 조회
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_TOKEN));

        return issueTokenPair(userId, user.getEmail());
    }

    // - 단일 세션 로그아웃 (D-007): 소유자 검증 후 1건만 폐기
    public void revokeOne(long userId, String refreshToken) {
        Claims claims = parseOrThrow(refreshToken);
        String tokenHash = HashUtils.sha256(refreshToken);

        RefreshToken rt = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_TOKEN));

        // 소유권 검증 (T-LOGOUT-03)
        if (!rt.getUserId().equals(userId)) {
            throw new AuthException(ErrorCode.INVALID_TOKEN);
        }

        // 이미 폐기 → idempotent 200 (T-LOGOUT-06)
        if (rt.getRevokedAt() != null) {
            return;
        }

        refreshTokenRepository.revokeById(rt.getId(), Instant.now(clock));
    }

    // - JWT 파싱 공통 처리: 만료 → TOKEN_EXPIRED, 그 외 → INVALID_TOKEN
    private Claims parseOrThrow(String token) {
        try {
            return jwtTokenProvider.parse(token);
        } catch (ExpiredJwtException e) {
            throw new AuthException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(ErrorCode.INVALID_TOKEN);
        }
    }
}
