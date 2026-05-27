package com.studymate.post.controller;

import com.studymate.auth.security.CustomUserDetails;
import com.studymate.post.command.PostCreateCommand;
import com.studymate.post.command.PostUpdateCommand;
import com.studymate.post.dto.request.PostCreateRequest;
import com.studymate.post.dto.request.PostUpdateRequest;
import com.studymate.post.dto.response.PostCreateResponse;
import com.studymate.post.dto.response.PostDetailResponse;
import com.studymate.post.dto.response.PostListResponse;
import com.studymate.post.dto.response.PostUpdateResponse;
import com.studymate.post.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams/{teamId}/posts")
@RequiredArgsConstructor
@Validated
public class PostController {

    private final PostService postService;

    @GetMapping
    public PostListResponse list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @RequestParam(defaultValue = "0")  @Min(0)            int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100)  int size
    ) {
        return postService.list(principal.getUserId(), teamId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostCreateResponse create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @Valid @RequestBody PostCreateRequest request
    ) {
        return postService.create(
                principal.getUserId(),
                teamId,
                new PostCreateCommand(request.title(), request.content(), request.type())
        );
    }

    @GetMapping("/{postId}")
    public PostDetailResponse detail(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long postId
    ) {
        return postService.detail(principal.getUserId(), teamId, postId);
    }

    @PatchMapping("/{postId}")
    public PostUpdateResponse update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        return postService.update(
                principal.getUserId(),
                teamId,
                postId,
                new PostUpdateCommand(request.title(), request.content())
        );
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long postId
    ) {
        postService.delete(principal.getUserId(), teamId, postId);
    }
}
