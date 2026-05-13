package com.studymate.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotBlank @Size(max = 255)
        String email,

        // - 영문+숫자 각 1자 이상, 8~20자 (SRS RE-SF1-01 / RE-NF-03)
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}$",
                 message = "비밀번호는 영문과 숫자를 각각 1자 이상 포함하여 8~20자여야 합니다.")
        String password,

        @NotBlank @Size(max = 100)
        String name
) {}
