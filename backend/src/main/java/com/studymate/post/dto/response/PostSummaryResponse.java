package com.studymate.post.dto.response;

import com.studymate.post.domain.PostType;

import java.time.LocalDateTime;

public record PostSummaryResponse(
        long          postId,
        String        title,
        PostType      type,
        String        authorName,
        LocalDateTime createdAt
) {}
