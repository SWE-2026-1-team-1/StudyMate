package com.studymate.study.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

public record StudyCreateRequest(
        @NotBlank @Size(min = 2, max = 50)  String title,
        @NotBlank @Size(max = 2000)         String description,
        @NotEmpty @Size(max = 10)           List<@NotBlank @Size(min = 1, max = 30) String> tags,
        @NotEmpty @Size(max = 10)           List<@NotBlank @Size(min = 1, max = 30) String> languages,
        @NotNull  @Min(2) @Max(50)          Integer maxMembers,
        @NotNull  @Min(1) @Max(52)          Integer durationWeeks,
        @NotBlank @Size(max = 50)           String meetingCycle
) {}
