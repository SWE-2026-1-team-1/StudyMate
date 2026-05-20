package com.studymate.study.dto.response;

import com.studymate.study.domain.StudyStatus;

import java.util.List;

public record StudySummaryResponse(
        long         studyId,
        String       title,
        List<String> tags,
        StudyStatus  status,
        int          currentMembers,
        int          maxMembers
) {}
