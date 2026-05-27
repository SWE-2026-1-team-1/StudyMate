package com.studymate.profile.controller;

import com.studymate.auth.security.CustomUserDetails;
import com.studymate.profile.dto.request.ProfileSaveRequest;
import com.studymate.profile.dto.response.ProfileResponse;
import com.studymate.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal CustomUserDetails principal) {
        return profileService.getProfile(principal.getUserId());
    }

    @PostMapping
    public ProfileResponse createProfile(@AuthenticationPrincipal CustomUserDetails principal,
                                         @Valid @RequestBody ProfileSaveRequest request) {
        return profileService.saveProfile(principal.getUserId(), request);
    }

    @PutMapping
    public ProfileResponse updateProfile(@AuthenticationPrincipal CustomUserDetails principal,
                                         @Valid @RequestBody ProfileSaveRequest request) {
        return profileService.saveProfile(principal.getUserId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@AuthenticationPrincipal CustomUserDetails principal) {
        profileService.deleteProfile(principal.getUserId());
    }
}
