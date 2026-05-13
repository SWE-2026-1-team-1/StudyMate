package com.studymate.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendEmailCodeRequest(
        // - 학교 이메일 도메인 강제 (SRS RE-SF1-01)
        @Email @NotBlank
        @Pattern(regexp = ".+@.+\\.ac\\.kr$", message = "학교 이메일만 사용 가능합니다.")
        String email
) {}
