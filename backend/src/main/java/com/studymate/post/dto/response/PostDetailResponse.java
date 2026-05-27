package com.studymate.post.dto.response;

import com.studymate.post.domain.PostType;

import java.time.LocalDateTime;

public record PostDetailResponse(
        long          postId,
        String        title,
        String        content,
        PostType      type,
        String        authorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
