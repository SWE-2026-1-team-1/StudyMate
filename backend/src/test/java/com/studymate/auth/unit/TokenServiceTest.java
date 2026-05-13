package com.studymate.auth.unit;

import com.studymate.auth.domain.RefreshToken;
import com.studymate.auth.exception.AuthException;
import com.studymate.auth.repository.RefreshTokenRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.security.JwtTokenProvider;
import com.studymate.auth.service.TokenService;
import com.studymate.auth.util.HashUtils;
import com.studymate.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T-RFRSH-07: revokeById affected=0 → revokeAllByUserId 호출 + INVALID_TOKEN
 * T-LOGOUT-08: row.userId != principal.userId → INVALID_TOKEN
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserRepository userRepository;

    private TokenService service;

    private static final long USER_ID   = 42L;
    private static final String RAW_TOKEN  = "raw-refresh-token";
    private static final String TOKEN_HASH = HashUtils.sha256(RAW_TOKEN);

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        service = new TokenService(jwtTokenProvider, refreshTokenRepository, userRepository, fixedClock);
    }

    // T-RFRSH-07: revokeById affected=0 (race) → revokeAllByUserId + INVALID_TOKEN
    @Test
    void T_RFRSH_07() {
        // Given: claims 에서 userId 추출 필요
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(USER_ID));
        when(jwtTokenProvider.parse(RAW_TOKEN)).thenReturn(claims);

        // RefreshToken 에 ID 주입 (JPA auto-generate 미사용 시 null)
        RefreshToken rt = RefreshToken.create(USER_ID, TOKEN_HASH,
                Instant.now().plus(14, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(rt, "id", 10L);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(rt));

        // revokeById → 0 (race condition)
        when(refreshTokenRepository.revokeById(eq(10L), any())).thenReturn(0);
        when(refreshTokenRepository.revokeAllByUserId(anyLong(), any())).thenReturn(1);

        // When / Then
        assertThatThrownBy(() -> service.rotate(RAW_TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));

        verify(refreshTokenRepository).revokeAllByUserId(eq(USER_ID), any());
    }

    // T-LOGOUT-08: row.userId != principal.userId → INVALID_TOKEN
    @Test
    void T_LOGOUT_08() {
        // Given: token 소유자 = USER_ID(42), 호출자 = 99 (불일치)
        // revokeOne 은 claims.getSubject() 를 사용하지 않으므로 stubbing 불필요
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.parse(RAW_TOKEN)).thenReturn(claims);

        RefreshToken rt = RefreshToken.create(USER_ID, TOKEN_HASH,
                Instant.now().plus(14, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(rt, "id", 10L);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(rt));

        long differentUserId = 99L;

        // When / Then
        assertThatThrownBy(() -> service.revokeOne(differentUserId, RAW_TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));

        verify(refreshTokenRepository, never()).revokeById(anyLong(), any());
    }
}
