package com.studymate.auth.config;

import com.studymate.auth.domain.EmailVerification;
import com.studymate.auth.domain.User;
import com.studymate.auth.repository.EmailVerificationRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 로컬 프로파일 전용 시드 데이터 초기화.
 * 멱등 — 이미 존재하는 데이터는 건드리지 않는다.
 *
 * 시드 계정
 *   login  테스트 : test@university.ac.kr / Test1234
 *   signup 테스트 : new@university.ac.kr (인증 코드 000000 으로 pre-verified)
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;

    static final String SEED_USER_EMAIL    = "test@university.ac.kr";
    static final String SEED_USER_PASSWORD = "Test1234";
    static final String SEED_USER_NAME     = "테스트유저";

    static final String SEED_VERIFY_EMAIL  = "new@university.ac.kr";
    static final String SEED_VERIFY_CODE   = "000000";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser();
        seedEmailVerification();
    }

    private void seedUser() {
        if (userRepository.existsByEmailAndIsDeletedFalse(SEED_USER_EMAIL)) return;
        userRepository.save(
                User.create(SEED_USER_EMAIL, passwordEncoder.encode(SEED_USER_PASSWORD), SEED_USER_NAME)
        );
        log.info("[LocalSeed] app_user 생성: {} / {}", SEED_USER_EMAIL, SEED_USER_PASSWORD);
    }

    private void seedEmailVerification() {
        Instant now = Instant.now();
        if (emailVerificationRepository.findUsableForSignup(SEED_VERIFY_EMAIL, now).isPresent()) return;

        EmailVerification ev = EmailVerification.create(
                SEED_VERIFY_EMAIL,
                HashUtils.sha256(SEED_VERIFY_CODE),
                now.plusSeconds(3600L * 24 * 365) // 1년 유효
        );
        ev.markVerified(now);
        emailVerificationRepository.save(ev);
        log.info("[LocalSeed] email_verification 생성: {} (코드: {})", SEED_VERIFY_EMAIL, SEED_VERIFY_CODE);
    }
}
