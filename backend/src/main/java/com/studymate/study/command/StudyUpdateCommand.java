package com.studymate.study.command;

import com.studymate.study.domain.StudyStatus;

import java.util.List;
import java.util.Optional;

public record StudyUpdateCommand(
        Optional<String>       title,
        Optional<String>       description,
        Optional<List<String>> tags,
        Optional<List<String>> languages,
        Optional<Integer>      maxMembers,
        Optional<Integer>      durationWeeks,
        Optional<String>       meetingCycle,
        Optional<StudyStatus>  status
) {}
