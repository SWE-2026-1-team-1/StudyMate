package com.studymate.study.dto.response;

import java.util.List;

public record MemberListResponse(
        List<MemberSummaryResponse> members,
        int                          totalCount
) {}
