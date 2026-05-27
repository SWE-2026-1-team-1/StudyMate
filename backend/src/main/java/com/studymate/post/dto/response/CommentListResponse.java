package com.studymate.post.dto.response;

import java.util.List;

public record CommentListResponse(
        List<CommentResponse> comments,
        int                    totalCount
) {}
