package com.studymate.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;  // SHA-256(refreshJwt)

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant revokedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    public static RefreshToken create(Long userId, String tokenHash, Instant expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.userId = userId;
        rt.tokenHash = tokenHash;
        rt.expiresAt = expiresAt;
        rt.createdAt = Instant.now();
        return rt;
    }

    // - idempotent: 이미 revoke 된 경우 변경 없음
    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public Long getId()            { return id; }
    public Long getUserId()        { return userId; }
    public String getTokenHash()   { return tokenHash; }
    public Instant getExpiresAt()  { return expiresAt; }
    public Instant getRevokedAt()  { return revokedAt; }
    public Instant getCreatedAt()  { return createdAt; }
}
