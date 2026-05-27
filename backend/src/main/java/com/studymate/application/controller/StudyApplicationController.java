package com.studymate.application.controller;

import com.studymate.application.command.ApplicationCreateCommand;
import com.studymate.application.dto.request.ApplicationCreateRequest;
import com.studymate.application.dto.response.ApplicationCreateResponse;
import com.studymate.application.service.ApplicationService;
import com.studymate.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/studies/{studyId}/applications")
@RequiredArgsConstructor
@Validated
public class StudyApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationCreateResponse apply(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long studyId,
            @Valid @RequestBody ApplicationCreateRequest request
    ) {
        return applicationService.apply(
                principal.getUserId(),
                studyId,
                new ApplicationCreateCommand(request.message())
        );
    }

    @DeleteMapping("/my")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMine(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long studyId
    ) {
        applicationService.cancelMine(principal.getUserId(), studyId);
    }
}
