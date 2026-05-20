package com.studymate.study.controller;

import com.studymate.auth.security.CustomUserDetails;
import com.studymate.study.command.StudyCreateCommand;
import com.studymate.study.command.StudyUpdateCommand;
import com.studymate.study.dto.request.StudyCreateRequest;
import com.studymate.study.dto.request.StudyUpdateRequest;
import com.studymate.study.dto.response.*;
import com.studymate.study.service.StudyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
@Validated
public class StudyController {

    private final StudyService studyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudyCreateResponse create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody StudyCreateRequest request
    ) {
        StudyCreateCommand command = new StudyCreateCommand(
                request.title(),
                request.description(),
                request.tags(),
                request.languages(),
                request.maxMembers(),
                request.durationWeeks(),
                request.meetingCycle()
        );
        return studyService.create(principal.getUserId(), command);
    }

    @GetMapping
    public StudyListResponse list(
            @RequestParam(defaultValue = "0")  @Min(0)         int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return studyService.list(page, size);
    }

    @GetMapping("/{studyId}")
    public StudyDetailResponse detail(
            @PathVariable long studyId
    ) {
        return studyService.detail(studyId);
    }

    @PatchMapping("/{studyId}")
    public StudyUpdateResponse update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long studyId,
            @Valid @RequestBody StudyUpdateRequest request
    ) {
        StudyUpdateCommand command = new StudyUpdateCommand(
                request.title(),
                request.description(),
                request.tags(),
                request.languages(),
                request.maxMembers(),
                request.durationWeeks(),
                request.meetingCycle(),
                request.status()
        );
        return studyService.update(principal.getUserId(), studyId, command);
    }

    @DeleteMapping("/{studyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long studyId
    ) {
        studyService.delete(principal.getUserId(), studyId);
    }
}
