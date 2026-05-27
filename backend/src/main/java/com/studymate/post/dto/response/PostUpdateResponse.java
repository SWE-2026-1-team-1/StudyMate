package com.studymate.post.dto.response;

import java.time.LocalDateTime;

public record PostUpdateResponse(
        long          postId,
        String        title,
        LocalDateTime updatedAt
) {}
