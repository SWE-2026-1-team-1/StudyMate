package com.studymate.application.controller;

import com.studymate.application.command.ApplicationRejectCommand;
import com.studymate.application.dto.request.ApplicationRejectRequest;
import com.studymate.application.dto.response.ApplicationApproveResponse;
import com.studymate.application.dto.response.ApplicationListResponse;
import com.studymate.application.dto.response.ApplicationRejectResponse;
import com.studymate.application.service.ApplicationService;
import com.studymate.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams/{teamId}/applications")
@RequiredArgsConstructor
@Validated
public class TeamApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ApplicationListResponse list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @RequestParam(defaultValue = "0")  @Min(0)         int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return applicationService.list(principal.getUserId(), teamId, page, size);
    }

    @PostMapping("/{applicationId}/approve")
    public ApplicationApproveResponse approve(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long applicationId
    ) {
        return applicationService.approve(principal.getUserId(), teamId, applicationId);
    }

    @PostMapping("/{applicationId}/reject")
    public ApplicationRejectResponse reject(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long applicationId,
            @Valid @RequestBody(required = false) ApplicationRejectRequest request
    ) {
        ApplicationRejectCommand command = request == null
                ? new ApplicationRejectCommand(null)
                : new ApplicationRejectCommand(request.rejectReason());
        return applicationService.reject(principal.getUserId(), teamId, applicationId, command);
    }
}
