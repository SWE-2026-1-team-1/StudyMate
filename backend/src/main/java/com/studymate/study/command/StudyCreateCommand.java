package com.studymate.study.command;

import java.util.List;

public record StudyCreateCommand(
        String       title,
        String       description,
        List<String> tags,
        List<String> languages,
        int          maxMembers,
        int          durationWeeks,
        String       meetingCycle
) {}
