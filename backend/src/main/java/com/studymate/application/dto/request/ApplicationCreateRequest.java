package com.studymate.application.dto.request;

import jakarta.validation.constraints.Size;

public record ApplicationCreateRequest(
        @Size(max = 500) String message
) {}
