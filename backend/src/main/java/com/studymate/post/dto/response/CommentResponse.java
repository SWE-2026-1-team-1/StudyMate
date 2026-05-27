package com.studymate.post.dto.response;

import java.time.LocalDateTime;

public record CommentResponse(
        long          commentId,
        String        content,
        String        authorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
