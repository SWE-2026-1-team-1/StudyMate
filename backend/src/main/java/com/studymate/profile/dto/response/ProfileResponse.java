package com.studymate.profile.dto.response;

import java.time.Instant;
import java.util.List;

public record ProfileResponse(
        long userId,
        String email,
        String name,
        String school,
        String major,
        String bio,
        List<String> interestTags,
        Instant createdAt,
        Instant updatedAt
) {}
