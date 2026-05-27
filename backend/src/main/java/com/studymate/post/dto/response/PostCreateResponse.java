package com.studymate.post.dto.response;

import java.time.LocalDateTime;

public record PostCreateResponse(
        long          postId,
        String        title,
        LocalDateTime createdAt
) {}
