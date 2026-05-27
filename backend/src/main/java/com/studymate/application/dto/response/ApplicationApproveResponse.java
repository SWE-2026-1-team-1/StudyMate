package com.studymate.application.dto.response;

import com.studymate.application.domain.ApplicationStatus;
import com.studymate.study.domain.StudyStatus;

import java.time.LocalDateTime;

public record ApplicationApproveResponse(
        long              applicationId,
        ApplicationStatus status,
        LocalDateTime     processedAt,
        StudyStatus       studyStatusAfter
) {}
