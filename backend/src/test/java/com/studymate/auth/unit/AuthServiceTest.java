package com.studymate.auth.unit;

import com.studymate.auth.domain.User;
import com.studymate.auth.dto.request.LoginRequest;
import com.studymate.auth.dto.request.SignupRequest;
import com.studymate.auth.exception.AuthException;
import com.studymate.auth.repository.UserRepository;
import com.studymate.auth.service.AuthService;
import com.studymate.auth.service.EmailVerificationService;
import com.studymate.auth.service.TokenService;
import com.studymate.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * T-SIGN-13: signup 호출 순서 — consumeVerifiedRecord → existsBy → save
 * T-LOGIN-06: login 호출 순서 — findByEmail → passwordEncoder.matches → tokenService.issueTokenPair
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock EmailVerificationService emailVerificationService;
    @Mock TokenService tokenService;
    @Mock PasswordEncoder passwordEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, emailVerificationService, tokenService, passwordEncoder);
    }

    // T-SIGN-13: consumeVerifiedRecord → existsByEmail → save 순서 보장
    @Test
    void T_SIGN_13() {
        // Given
        doNothing().when(emailVerificationService).consumeVerifiedRecord(anyString());
        when(userRepository.existsByEmailAndIsDeletedFalse(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        // 저장 후 반환될 User 에 ID 주입
        User savedUser = User.create("test@university.ac.kr", "hash", "Name");
        ReflectionTestUtils.setField(savedUser, "id", 1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        SignupRequest req = new SignupRequest("test@university.ac.kr", "Password1", "Name");

        // When
        service.signup(req);

        // Then: InOrder 로 호출 순서 확인
        InOrder inOrder = inOrder(emailVerificationService, userRepository);
        inOrder.verify(emailVerificationService).consumeVerifiedRecord("test@university.ac.kr");
        inOrder.verify(userRepository).existsByEmailAndIsDeletedFalse("test@university.ac.kr");
        inOrder.verify(userRepository).save(any(User.class));
    }

    // T-LOGIN-06: passwordEncoder.matches → tokenService.issueTokenPair 호출 순서
    @Test
    void T_LOGIN_06() {
        // Given: user 에 ID 주입
        User mockUser = User.create("a@test.com", "hashed", "Name");
        ReflectionTestUtils.setField(mockUser, "id", 1L);
        when(userRepository.findByEmailAndIsDeletedFalse("a@test.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("Pass1234", "hashed")).thenReturn(true);
        when(tokenService.issueTokenPair(anyLong(), anyString()))
                .thenReturn(new TokenService.TokenPair("access", "refresh"));

        LoginRequest req = new LoginRequest("a@test.com", "Pass1234");

        // When
        service.login(req);

        // Then: InOrder
        InOrder inOrder = inOrder(userRepository, passwordEncoder, tokenService);
        inOrder.verify(userRepository).findByEmailAndIsDeletedFalse("a@test.com");
        inOrder.verify(passwordEncoder).matches("Pass1234", "hashed");
        inOrder.verify(tokenService).issueTokenPair(anyLong(), anyString());
    }

    // T-LOGIN-06 보완: 잘못된 비밀번호 → INVALID_CREDENTIALS, tokenService 미호출
    @Test
    void T_LOGIN_06_wrongPassword() {
        User mockUser = User.create("a@test.com", "hashed", "Name");
        ReflectionTestUtils.setField(mockUser, "id", 1L);
        when(userRepository.findByEmailAndIsDeletedFalse("a@test.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("Wrong", "hashed")).thenReturn(false);

        LoginRequest req = new LoginRequest("a@test.com", "Wrong");

        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(tokenService, never()).issueTokenPair(anyLong(), anyString());
    }
}
