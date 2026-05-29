package com.studymate.study.dto.response;

import com.studymate.study.domain.StudyStatus;

import java.util.List;

public record MyStudyItem(
        long         studyId,
        long         teamId,
        String       title,
        List<String> tags,
        StudyStatus  status,
        String       role,
        int          currentMembers,
        int          maxMembers,
        String       meetingCycle,
        int          durationWeeks
) {}
