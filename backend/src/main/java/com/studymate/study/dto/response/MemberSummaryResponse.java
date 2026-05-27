package com.studymate.study.dto.response;

import java.time.LocalDateTime;

public record MemberSummaryResponse(
        long          memberId,
        long          userId,
        String        userName,
        String        roleCode,
        LocalDateTime joinedAt
) {}
