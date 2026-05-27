package com.studymate.post.dto.request;

import jakarta.validation.constraints.Size;

public record PostUpdateRequest(
        @Size(min = 1, max = 300, message = "제목은 1자 이상 300자 이하여야 합니다.")
        String title,

        @Size(min = 1, max = 5000, message = "본문은 1자 이상 5000자 이하여야 합니다.")
        String content
) {}
