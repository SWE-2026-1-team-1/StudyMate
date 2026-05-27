package com.studymate.study.dto.response;

import com.studymate.study.domain.StudyStatus;

import java.time.LocalDateTime;
import java.util.List;

public record StudyDetailResponse(
        long            studyId,
        String          title,
        String          description,
        List<String>    tags,
        List<String>    languages,
        int             maxMembers,
        int             currentMembers,
        String          meetingCycle,
        int             durationWeeks,
        StudyStatus     status,
        CreatedBy       createdBy,
        LocalDateTime   createdAt
) {
    public record CreatedBy(long userId, String name) {}
}
