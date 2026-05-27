package com.studymate.application.dto.response;

import com.studymate.application.domain.ApplicationStatus;

import java.time.LocalDateTime;

public record ApplicationRejectResponse(
        long              applicationId,
        ApplicationStatus status,
        LocalDateTime     processedAt,
        String            rejectReason
) {}
