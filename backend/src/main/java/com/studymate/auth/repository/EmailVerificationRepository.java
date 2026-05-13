package com.studymate.auth.repository;

import com.studymate.auth.domain.EmailVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    // - verify 단계: 가장 최근 미소비 row (비관적 락)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(String email);

    // - signup 단계: verified + 미소비 + 미만료 row (비관적 락)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select e from EmailVerification e
           where e.email = :email
             and e.verifiedAt is not null
             and e.consumedAt is null
             and e.expiresAt > :now
           order by e.verifiedAt desc
           """)
    Optional<EmailVerification> findUsableForSignup(@Param("email") String email, @Param("now") Instant now);

    // - 쿨다운 체크: 마지막 발송 시각
    @Query("select e.createdAt from EmailVerification e where e.email = :email order by e.createdAt desc limit 1")
    Optional<Instant> findLastSentAtByEmail(@Param("email") String email);
}
