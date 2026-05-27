package com.studymate.application.dto.response;

import com.studymate.application.domain.ApplicationStatus;

import java.time.LocalDateTime;

public record ApplicationCreateResponse(
        long              applicationId,
        long              studyId,
        ApplicationStatus status,
        LocalDateTime     appliedAt
) {}
