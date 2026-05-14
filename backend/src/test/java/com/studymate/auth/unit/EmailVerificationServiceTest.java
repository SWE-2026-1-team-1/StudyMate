package com.studymate.auth.unit;

import com.studymate.auth.domain.EmailVerification;
import com.studymate.auth.mail.EmailSender;
import com.studymate.auth.repository.EmailVerificationRepository;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.service.EmailVerificationService;
import com.studymate.auth.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T-SEND-08: EmailVerificationService.sendCode 단위 테스트.
 * 코드 생성 → SHA-256 해시 저장 → emailSender.send 호출 검증.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock EmailVerificationRepository repository;
    @Mock UserRepository userRepository;
    @Mock EmailSender emailSender;

    private EmailVerificationService service;

    private static final String EMAIL = "test@university.ac.kr";
    private static final Instant NOW  = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new EmailVerificationService(repository, userRepository, emailSender, fixedClock);
    }

    // T-SEND-08: 정상 플로우 — 6자리 코드 생성, SHA-256 해시 row 저장, emailSender.send 호출
    @Test
    void T_SEND_08() {
        // Given
        when(userRepository.existsByEmailAndIsDeletedFalse(EMAIL)).thenReturn(false);
        when(repository.findLastSentAtByEmail(EMAIL)).thenReturn(Optional.empty());

        ArgumentCaptor<EmailVerification> evCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        when(repository.save(evCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(emailSender).send(eq(EMAIL), codeCaptor.capture());

        // When
        service.sendCode(EMAIL);

        // Then — 상호작용
        verify(emailSender, times(1)).send(eq(EMAIL), any());
        verify(repository, times(1)).save(any(EmailVerification.class));

        // Then — 동작: 6자리 숫자 코드
        String plainCode = codeCaptor.getValue();
        assertThat(plainCode).matches("\\d{6}");

        // Then — 상태: SHA-256 해시 일치, 길이 64
        EmailVerification ev = evCaptor.getValue();
        assertThat(ev.getCodeHash()).isEqualTo(HashUtils.sha256(plainCode));
        assertThat(ev.getCodeHash()).hasSize(64);
        assertThat(ev.getExpiresAt()).isEqualTo(NOW.plusSeconds(60 * 10));  // TTL 10분
    }
}
