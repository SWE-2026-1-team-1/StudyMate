package com.studymate.application.controller;

import com.studymate.application.dto.response.MyApplicationListResponse;
import com.studymate.application.service.ApplicationService;
import com.studymate.auth.security.CustomUserDetails;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage/applications")
@RequiredArgsConstructor
@Validated
public class MyPageApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public MyApplicationListResponse listMine(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "0")  @Min(0)         int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return applicationService.listMine(principal.getUserId(), page, size);
    }
}
