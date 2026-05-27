package com.studymate.post.controller;

import com.studymate.auth.security.CustomUserDetails;
import com.studymate.post.command.CommentCreateCommand;
import com.studymate.post.command.CommentUpdateCommand;
import com.studymate.post.dto.request.CommentCreateRequest;
import com.studymate.post.dto.request.CommentUpdateRequest;
import com.studymate.post.dto.response.CommentListResponse;
import com.studymate.post.dto.response.CommentResponse;
import com.studymate.post.service.PostCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams/{teamId}/posts/{postId}/comments")
@RequiredArgsConstructor
@Validated
public class PostCommentController {

    private final PostCommentService postCommentService;

    @GetMapping
    public CommentListResponse list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long postId
    ) {
        return postCommentService.list(principal.getUserId(), teamId, postId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return postCommentService.create(
                principal.getUserId(),
                teamId,
                postId,
                new CommentCreateCommand(request.content())
        );
    }

    @PatchMapping("/{commentId}")
    public CommentResponse update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long postId,
            @PathVariable long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        return postCommentService.update(
                principal.getUserId(),
                teamId,
                postId,
                commentId,
                new CommentUpdateCommand(request.content())
        );
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable long teamId,
            @PathVariable long postId,
            @PathVariable long commentId
    ) {
        postCommentService.delete(principal.getUserId(), teamId, postId, commentId);
    }
}
