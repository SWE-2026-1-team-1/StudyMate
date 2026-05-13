package com.studymate.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_verification")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String codeHash;   // SHA-256(plainCode) — 평문 저장 금지

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant verifiedAt;

    @Column
    private Instant consumedAt;

    @Column(nullable = false)
    private int attemptCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailVerification() {}

    public static EmailVerification create(String email, String codeHash, Instant expiresAt) {
        EmailVerification ev = new EmailVerification();
        ev.email = email;
        ev.codeHash = codeHash;
        ev.expiresAt = expiresAt;
        ev.attemptCount = 0;
        ev.createdAt = Instant.now();
        return ev;
    }

    // - verifiedAt 이 null 이고 아직 만료 안 됐을 때만 set
    public void markVerified(Instant now) {
        if (verifiedAt == null && expiresAt.isAfter(now)) {
            this.verifiedAt = now;
        }
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    // - idempotent
    public void markConsumed(Instant now) {
        if (consumedAt == null) {
            this.consumedAt = now;
        }
    }

    // - 가입에 사용 가능한 record 여부 (D-005)
    public boolean isUsableForSignup(Instant now) {
        return verifiedAt != null && consumedAt == null && expiresAt.isAfter(now);
    }

    public Long getId()            { return id; }
    public String getEmail()       { return email; }
    public String getCodeHash()    { return codeHash; }
    public Instant getExpiresAt()  { return expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public int getAttemptCount()   { return attemptCount; }
    public Instant getCreatedAt()  { return createdAt; }
}
