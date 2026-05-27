package com.studymate.study.dto.response;

import com.studymate.study.domain.StudyStatus;

import java.time.LocalDateTime;

public record StudyUpdateResponse(
        long          studyId,
        String        title,
        StudyStatus   status,
        LocalDateTime updatedAt
) {}
