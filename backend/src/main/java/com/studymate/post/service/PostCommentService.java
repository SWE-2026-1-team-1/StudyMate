package com.studymate.post.service;

import com.studymate.auth.domain.User;
import com.studymate.auth.repository.UserRepository;
import com.studymate.common.exception.ErrorCode;
import com.studymate.post.command.CommentCreateCommand;
import com.studymate.post.command.CommentUpdateCommand;
import com.studymate.post.domain.Post;
import com.studymate.post.domain.PostComment;
import com.studymate.post.dto.response.CommentListResponse;
import com.studymate.post.dto.response.CommentResponse;
import com.studymate.post.exception.PostException;
import com.studymate.post.repository.PostCommentRepository;
import com.studymate.post.repository.PostRepository;
import com.studymate.study.domain.StudyMember;
import com.studymate.study.repository.StudyMemberRepository;
import com.studymate.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostCommentService {

    private final PostCommentRepository postCommentRepository;
    private final PostRepository        postRepository;
    private final StudyRepository       studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final UserRepository        userRepository;

    @Transactional(readOnly = true)
    public CommentListResponse list(long callerUserId, long studyId, long postId) {
        assertStudyAndActiveMember(callerUserId, studyId);
        Post post = requireActivePost(studyId, postId);

        List<PostComment> comments = postCommentRepository
                .findAllByPostIdAndNotDeletedOrderByCreatedAtAsc(post.getId());

        Map<Long, String> authorNames = resolveAuthorNames(comments.stream()
                .map(PostComment::getAuthorMemberId)
                .toList());

        List<CommentResponse> rows = comments.stream()
                .map(c -> new CommentResponse(
                        c.getId(),
                        c.getContent(),
                        authorNames.getOrDefault(c.getAuthorMemberId(), ""),
                        c.getCreatedAt(),
                        c.getUpdatedAt()
                ))
                .toList();

        return new CommentListResponse(rows, rows.size());
    }

    @Transactional
    public CommentResponse create(long callerUserId, long studyId, long postId, CommentCreateCommand command) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember caller = studyMemberRepository
                .findByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)
                .orElseThrow(() -> new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다."));

        Post post = requireActivePost(studyId, postId);

        PostComment saved = postCommentRepository.save(
                PostComment.create(studyId, post.getId(), caller.getId(), command.content())
        );

        String authorName = resolveAuthorNames(List.of(caller.getId()))
                .getOrDefault(caller.getId(), "");

        return new CommentResponse(
                saved.getId(),
                saved.getContent(),
                authorName,
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public CommentResponse update(long callerUserId, long studyId, long postId, long commentId,
                                  CommentUpdateCommand command) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember caller = studyMemberRepository
                .findByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)
                .orElseThrow(() -> new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다."));

        requireActivePost(studyId, postId);

        PostComment comment = postCommentRepository.findByIdAndPostIdAndNotDeletedForUpdate(commentId, postId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다."));

        if (comment.getStudyId() != studyId) {
            throw new PostException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (comment.getAuthorMemberId() != caller.getId()) {
            throw new PostException(ErrorCode.FORBIDDEN, "댓글 수정 권한이 없습니다.");
        }

        comment.update(command.content());

        String authorName = resolveAuthorNames(List.of(caller.getId()))
                .getOrDefault(caller.getId(), "");

        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                authorName,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    @Transactional
    public void delete(long callerUserId, long studyId, long postId, long commentId) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember caller = studyMemberRepository
                .findByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)
                .orElseThrow(() -> new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다."));

        requireActivePost(studyId, postId);

        PostComment comment = postCommentRepository.findByIdAndPostIdAndNotDeletedForUpdate(commentId, postId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다."));

        if (comment.getStudyId() != studyId) {
            throw new PostException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (comment.getAuthorMemberId() != caller.getId()) {
            throw new PostException(ErrorCode.FORBIDDEN, "댓글 삭제 권한이 없습니다.");
        }

        comment.softDelete();
    }

    private void assertStudyAndActiveMember(long callerUserId, long studyId) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));
        if (!studyMemberRepository.existsByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)) {
            throw new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다.");
        }
    }

    private Post requireActivePost(long studyId, long postId) {
        return postRepository.findByIdAndStudyIdAndNotDeleted(postId, studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다."));
    }

    private Map<Long, String> resolveAuthorNames(List<Long> authorMemberIds) {
        if (authorMemberIds.isEmpty()) return Map.of();

        List<StudyMember> members = studyMemberRepository.findAllById(authorMemberIds);
        Map<Long, Long> userIdByMemberId = members.stream()
                .collect(Collectors.toMap(StudyMember::getId, StudyMember::getUserId));

        List<Long> userIds = userIdByMemberId.values().stream().distinct().toList();
        Map<Long, String> nameByUserId = userRepository.findAllByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        return userIdByMemberId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> nameByUserId.getOrDefault(e.getValue(), "")
                ));
    }
}
