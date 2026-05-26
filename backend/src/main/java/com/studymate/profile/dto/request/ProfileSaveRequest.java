package com.studymate.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileSaveRequest(
        @NotBlank @Size(max = 100)
        String name,

        @Size(max = 100)
        String school,

        @Size(max = 100)
        String major,

        @Size(max = 255)
        String bio,

        @Size(max = 10)
        List<@Size(max = 30) String> interestTags
) {}
