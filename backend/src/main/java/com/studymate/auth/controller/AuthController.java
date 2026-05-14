package com.studymate.auth.controller;

import com.studymate.auth.dto.request.*;
import com.studymate.auth.dto.response.LoginResponse;
import com.studymate.auth.dto.response.SignupResponse;
import com.studymate.auth.dto.response.TokenRefreshResponse;
import com.studymate.auth.security.CustomUserDetails;
import com.studymate.auth.service.AuthService;
import com.studymate.auth.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // - D-007: refreshToken 본문 필수
    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal CustomUserDetails principal,
                       @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(principal.getUserId(), request.refreshToken());
    }

    @PostMapping("/email/send-code")
    public void sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        emailVerificationService.sendCode(request.email());
    }

    @PostMapping("/email/verify")
    public void verifyEmailCode(@Valid @RequestBody VerifyEmailCodeRequest request) {
        emailVerificationService.verifyCode(request.email(), request.code());
    }

    @PostMapping("/token/refresh")
    public TokenRefreshResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }
}
