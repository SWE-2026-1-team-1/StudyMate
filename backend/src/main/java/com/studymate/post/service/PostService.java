package com.studymate.post.service;

import com.studymate.auth.domain.User;
import com.studymate.auth.repository.UserRepository;
import com.studymate.common.exception.ErrorCode;
import com.studymate.post.command.PostCreateCommand;
import com.studymate.post.command.PostUpdateCommand;
import com.studymate.post.domain.Post;
import com.studymate.post.domain.PostType;
import com.studymate.post.dto.response.PostCreateResponse;
import com.studymate.post.dto.response.PostDetailResponse;
import com.studymate.post.dto.response.PostListResponse;
import com.studymate.post.dto.response.PostSummaryResponse;
import com.studymate.post.dto.response.PostUpdateResponse;
import com.studymate.post.exception.PostException;
import com.studymate.post.repository.PostRepository;
import com.studymate.study.domain.StudyMember;
import com.studymate.study.repository.StudyMemberRepository;
import com.studymate.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository        postRepository;
    private final StudyRepository       studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final UserRepository        userRepository;

    @Transactional(readOnly = true)
    public PostListResponse list(long callerUserId, long studyId, int page, int size) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        if (!studyMemberRepository.existsByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)) {
            throw new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다.");
        }

        Page<Post> postPage = postRepository.findAllByStudyIdAndNotDeleted(studyId, PageRequest.of(page, size));

        Map<Long, String> authorNames = resolveAuthorNames(postPage.getContent().stream()
                .map(Post::getAuthorMemberId)
                .toList());

        List<PostSummaryResponse> rows = postPage.getContent().stream()
                .map(p -> new PostSummaryResponse(
                        p.getId(),
                        p.getTitle(),
                        p.getType(),
                        authorNames.getOrDefault(p.getAuthorMemberId(), ""),
                        p.getCreatedAt()
                ))
                .toList();

        return new PostListResponse(rows, postPage.getTotalElements());
    }

    @Transactional
    public PostCreateResponse create(long callerUserId, long studyId, PostCreateCommand command) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember caller = studyMemberRepository
                .findByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)
                .orElseThrow(() -> new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다."));

        if (command.type() == PostType.NOTICE) {
            studyMemberRepository.findActiveNoticeWriter(studyId, callerUserId)
                    .orElseThrow(() -> new PostException(ErrorCode.FORBIDDEN, "공지 작성 권한이 없습니다."));
        }

        Post saved = postRepository.save(
                Post.create(studyId, caller.getId(), command.type(), command.title(), command.content())
        );

        return new PostCreateResponse(saved.getId(), saved.getTitle(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PostDetailResponse detail(long callerUserId, long studyId, long postId) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        if (!studyMemberRepository.existsByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)) {
            throw new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다.");
        }

        Post post = postRepository.findByIdAndStudyIdAndNotDeleted(postId, studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다."));

        String authorName = resolveAuthorNames(List.of(post.getAuthorMemberId()))
                .getOrDefault(post.getAuthorMemberId(), "");

        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getType(),
                authorName,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    @Transactional
    public PostUpdateResponse update(long callerUserId, long studyId, long postId, PostUpdateCommand command) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember caller = studyMemberRepository
                .findByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)
                .orElseThrow(() -> new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다."));

        Post post = postRepository.findByIdAndStudyIdAndNotDeletedForUpdate(postId, studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다."));

        if (post.getAuthorMemberId() != caller.getId()) {
            throw new PostException(ErrorCode.FORBIDDEN, "게시글 수정 권한이 없습니다.");
        }

        post.update(command.title(), command.content());

        return new PostUpdateResponse(post.getId(), post.getTitle(), post.getUpdatedAt());
    }

    @Transactional
    public void delete(long callerUserId, long studyId, long postId) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember caller = studyMemberRepository
                .findByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)
                .orElseThrow(() -> new PostException(ErrorCode.FORBIDDEN, "팀 접근 권한이 없습니다."));

        Post post = postRepository.findByIdAndStudyIdAndNotDeletedForUpdate(postId, studyId)
                .orElseThrow(() -> new PostException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다."));

        boolean isAuthor = post.getAuthorMemberId() == caller.getId();
        boolean isLeader = !isAuthor && studyMemberRepository.findActiveManager(studyId, callerUserId).isPresent();
        if (!(isAuthor || isLeader)) {
            throw new PostException(ErrorCode.FORBIDDEN, "게시글 삭제 권한이 없습니다.");
        }

        post.softDelete();
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
