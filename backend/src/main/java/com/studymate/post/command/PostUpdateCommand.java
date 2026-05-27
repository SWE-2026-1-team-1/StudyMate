package com.studymate.post.command;

public record PostUpdateCommand(
        String title,
        String content
) {}
