package com.studymate.application.dto.response;

import java.util.List;

public record ApplicationListResponse(
        List<ApplicationSummaryResponse> applications,
        long  totalCount,
        int   page,
        int   size
) {}
