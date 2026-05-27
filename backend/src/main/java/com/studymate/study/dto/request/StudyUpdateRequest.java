package com.studymate.study.dto.request;

import com.studymate.study.domain.StudyStatus;

import java.util.List;
import java.util.Optional;

/**
 * PATCH /api/studies/{studyId} 요청 DTO.
 * 미전송 = Optional.empty() (변경 없음). 명시적 null 도 동일 처리.
 * 값이 존재할 때의 유효성 검증은 StudyService 에서 수행.
 */
public record StudyUpdateRequest(
        Optional<String>       title,
        Optional<String>       description,
        Optional<List<String>> tags,
        Optional<List<String>> languages,
        Optional<Integer>      maxMembers,
        Optional<Integer>      durationWeeks,
        Optional<String>       meetingCycle,
        Optional<StudyStatus>  status
) {
    public StudyUpdateRequest {
        if (title        == null) title        = Optional.empty();
        if (description  == null) description  = Optional.empty();
        if (tags         == null) tags         = Optional.empty();
        if (languages    == null) languages    = Optional.empty();
        if (maxMembers   == null) maxMembers   = Optional.empty();
        if (durationWeeks== null) durationWeeks= Optional.empty();
        if (meetingCycle == null) meetingCycle = Optional.empty();
        if (status       == null) status       = Optional.empty();
    }
}
