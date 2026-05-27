package com.studymate.post.command;

import com.studymate.post.domain.PostType;

public record PostCreateCommand(
        String   title,
        String   content,
        PostType type
) {}
