package com.studymate.application.dto.response;

import java.time.LocalDateTime;

public record ApplicationSummaryResponse(
        long          applicationId,
        long          applicantUserId,
        String        applicantName,
        String        message,
        LocalDateTime appliedAt
) {}
