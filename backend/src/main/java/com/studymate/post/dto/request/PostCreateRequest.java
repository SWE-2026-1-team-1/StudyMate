package com.studymate.post.dto.request;

import com.studymate.post.domain.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotBlank(message = "제목은 비어 있을 수 없습니다.")
        @Size(min = 1, max = 300, message = "제목은 1자 이상 300자 이하여야 합니다.")
        String title,

        @NotBlank(message = "본문은 비어 있을 수 없습니다.")
        @Size(min = 1, max = 5000, message = "본문은 1자 이상 5000자 이하여야 합니다.")
        String content,

        @NotNull(message = "게시글 유형은 필수입니다.")
        PostType type
) {}
