package com.studymate.auth.service;

import com.studymate.auth.domain.EmailVerification;
import com.studymate.auth.exception.AuthException;
import com.studymate.auth.mail.EmailSender;
import com.studymate.auth.repository.EmailVerificationRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.util.HashUtils;
import com.studymate.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository repository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final Clock clock;

    // - D-008 정책 상수
    static final int CODE_LENGTH        = 6;
    static final long TTL_MINUTES       = 10;
    static final int MAX_ATTEMPTS       = 5;
    static final long COOLDOWN_SECONDS  = 60;

    public void sendCode(String email) {
        // 이미 가입된 이메일 → CONFLICT
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new AuthException(ErrorCode.CONFLICT);
        }

        Instant now = Instant.now(clock);

        // 60초 쿨다운 체크 (D-008)
        repository.findLastSentAtByEmail(email).ifPresent(lastSentAt -> {
            long secondsAgo = ChronoUnit.SECONDS.between(lastSentAt, now);
            if (secondsAgo < COOLDOWN_SECONDS) {
                throw new AuthException(ErrorCode.INVALID_INPUT, "잠시 후 다시 시도해주세요");
            }
        });

        // 6자리 코드 생성 + SHA-256 해시 저장 (D-008)
        String plainCode = generateCode();
        String codeHash  = HashUtils.sha256(plainCode);
        Instant expiresAt = now.plus(TTL_MINUTES, ChronoUnit.MINUTES);

        repository.save(EmailVerification.create(email, codeHash, expiresAt));
        emailSender.send(email, plainCode);
    }

    // - attempt increment 는 INVALID_EMAIL_CODE 예외 시에도 커밋해야 하므로 noRollbackFor 지정
    @Transactional(noRollbackFor = AuthException.class)
    public void verifyCode(String email, String code) {
        Instant now = Instant.now(clock);

        EmailVerification ev = repository
                .findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_EMAIL_CODE));

        // 시도 횟수 초과 → 정답이라도 거절 (D-008)
        if (ev.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new AuthException(ErrorCode.INVALID_EMAIL_CODE);
        }

        // 만료 체크 (시도 횟수 증가 전)
        if (!ev.getExpiresAt().isAfter(now)) {
            throw new AuthException(ErrorCode.EXPIRED_EMAIL_CODE);
        }

        // 코드 불일치: 횟수 증가 후 예외 (커밋 필요 — noRollbackFor)
        if (!HashUtils.sha256(code).equals(ev.getCodeHash())) {
            ev.incrementAttempt();
            throw new AuthException(ErrorCode.INVALID_EMAIL_CODE);
        }

        // 정답: verifiedAt 설정 (횟수 증가 없음)
        ev.markVerified(now);
    }

    // - 독립 트랜잭션으로 소비 확정 (T-SIGN-06: CONFLICT 시에도 소비 커밋)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeVerifiedRecord(String email) {
        Instant now = Instant.now(clock);

        EmailVerification ev = repository.findUsableForSignup(email, now)
                .orElseThrow(() -> new AuthException(ErrorCode.EMAIL_NOT_VERIFIED));

        ev.markConsumed(now);
    }

    // - SecureRandom 6자리 숫자 문자열 (leading zero 포함, D-008)
    private static String generateCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }
}
