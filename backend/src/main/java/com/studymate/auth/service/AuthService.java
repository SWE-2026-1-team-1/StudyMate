package com.studymate.auth.service;

import com.studymate.auth.domain.User;
import com.studymate.auth.dto.request.LoginRequest;
import com.studymate.auth.dto.request.SignupRequest;
import com.studymate.auth.dto.response.LoginResponse;
import com.studymate.auth.dto.response.SignupResponse;
import com.studymate.auth.dto.response.TokenRefreshResponse;
import com.studymate.auth.exception.AuthException;
import com.studymate.auth.repository.UserRepository;
import com.studymate.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    // - consumeVerifiedRecord 가 REQUIRES_NEW 독립 트랜잭션이므로 여기는 기본 @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 이메일 인증 record 소비 (없으면 EMAIL_NOT_VERIFIED 던짐)
        emailVerificationService.consumeVerifiedRecord(request.email());

        // 중복 이메일 확인 (소비 후 체크 → 충돌 시 EV는 이미 소비됨, 재인증 필요)
        if (userRepository.existsByEmailAndIsDeletedFalse(request.email())) {
            throw new AuthException(ErrorCode.CONFLICT);
        }

        String hash = passwordEncoder.encode(request.password());
        User saved  = userRepository.save(User.create(request.email(), hash, request.name()));

        return new SignupResponse(saved.getId(), saved.getEmail(), saved.getName());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndIsDeletedFalse(request.email())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        // 존재 여부와 동일한 에러 코드로 사용자 존재 여부 누설 방지
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        TokenService.TokenPair pair = tokenService.issueTokenPair(user.getId(), user.getEmail());
        return new LoginResponse(pair.accessToken(), pair.refreshToken(), user.getId());
    }

    // - D-007 단일 세션 로그아웃
    public void logout(long userId, String refreshToken) {
        tokenService.revokeOne(userId, refreshToken);
    }

    public TokenRefreshResponse refresh(String refreshToken) {
        TokenService.TokenPair pair = tokenService.rotate(refreshToken);
        return new TokenRefreshResponse(pair.accessToken(), pair.refreshToken());
    }
}
