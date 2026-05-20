package com.studymate.study.dto.response;

import java.util.List;

public record StudyListResponse(
        List<StudySummaryResponse> studies,
        long totalCount,
        int  page,
        int  size
) {}
