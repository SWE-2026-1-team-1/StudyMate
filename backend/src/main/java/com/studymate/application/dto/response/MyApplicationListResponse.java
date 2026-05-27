package com.studymate.application.dto.response;

import java.util.List;

public record MyApplicationListResponse(
        List<MyApplicationResponse> applications,
        long  totalCount,
        int   page,
        int   size
) {}
