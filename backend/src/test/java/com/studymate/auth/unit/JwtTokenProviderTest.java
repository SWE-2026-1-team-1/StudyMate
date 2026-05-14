package com.studymate.auth.unit;

import com.studymate.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-LOGIN-07: JwtTokenProvider 단위 테스트 — round-trip, 만료, 변조 검증.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-testing-purposes-at-least-32-chars";
    private static final long ACCESS_MS  = 300_000L;   // 5분
    private static final long REFRESH_MS = 86_400_000L; // 24시간

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, ACCESS_MS, REFRESH_MS);
    }

    // T-LOGIN-07a: createAccessToken round-trip — subject = userId, email claim 존재
    @Test
    void T_LOGIN_07_accessToken_roundTrip() {
        long userId = 7L;
        String email = "a@university.ac.kr";

        String token = provider.createAccessToken(userId, email);
        Claims claims = provider.parse(token);

        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(claims.get("email", String.class)).isEqualTo(email);
    }

    // T-LOGIN-07b: createRefreshToken round-trip — subject = userId
    @Test
    void T_LOGIN_07_refreshToken_roundTrip() {
        long userId = 7L;

        String token = provider.createRefreshToken(userId);
        Claims claims = provider.parse(token);

        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
    }

    // T-LOGIN-07c: 만료된 토큰 → ExpiredJwtException
    @Test
    void T_LOGIN_07_expired() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        String expired = Jwts.builder()
                .subject("1")
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(Date.from(past.minusSeconds(1800)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> provider.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    // T-LOGIN-07d: 잘못된 서명 → JwtException
    @Test
    void T_LOGIN_07_wrongSignature() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-for-testing-purposes-at-least-32-chars"
                        .getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String tampered = Jwts.builder()
                .subject("1")
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();

        assertThatThrownBy(() -> provider.parse(tampered))
                .isInstanceOf(JwtException.class);
    }
}
