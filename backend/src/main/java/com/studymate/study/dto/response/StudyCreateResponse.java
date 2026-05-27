package com.studymate.study.dto.response;

import com.studymate.study.domain.StudyStatus;

public record StudyCreateResponse(
        long        studyId,
        String      title,
        StudyStatus status,
        int         currentMembers,
        int         maxMembers
) {}
