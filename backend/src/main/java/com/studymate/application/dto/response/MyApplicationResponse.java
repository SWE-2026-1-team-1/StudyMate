package com.studymate.application.dto.response;

import com.studymate.application.domain.ApplicationStatus;
import com.studymate.study.domain.StudyStatus;

import java.time.LocalDateTime;

public record MyApplicationResponse(
        long              applicationId,
        long              studyId,
        String            studyTitle,
        StudyStatus       studyStatus,
        ApplicationStatus status,
        LocalDateTime     appliedAt,
        LocalDateTime     processedAt
) {}
