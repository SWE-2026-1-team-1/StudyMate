package com.studymate.study.controller;

import com.studymate.auth.security.CustomUserDetails;
import com.studymate.study.dto.response.MemberListResponse;
import com.studymate.study.service.StudyMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams/{teamId}/members")
@RequiredArgsConstructor
public class StudyMemberController {

    private final StudyMemberService studyMemberService;

    @GetMapping
    public MemberListResponse list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId
    ) {
        return studyMemberService.list(principal.getUserId(), teamId);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long memberId
    ) {
        studyMemberService.kick(principal.getUserId(), teamId, memberId);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId
    ) {
        studyMemberService.leave(principal.getUserId(), teamId);
    }
}
