package com.studymate.study.service;

import com.studymate.auth.domain.User;
import com.studymate.auth.repository.UserRepository;
import com.studymate.common.exception.ErrorCode;
import com.studymate.study.domain.Study;
import com.studymate.study.domain.StudyMember;
import com.studymate.study.dto.response.MemberListResponse;
import com.studymate.study.dto.response.MemberSummaryResponse;
import com.studymate.study.exception.StudyException;
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
public class StudyMemberService {

    private final StudyMemberRepository studyMemberRepository;
    private final StudyRepository       studyRepository;
    private final UserRepository        userRepository;

    @Transactional(readOnly = true)
    public MemberListResponse list(long callerUserId, long studyId) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        if (!studyMemberRepository.existsByStudyIdAndUserIdAndIsActiveTrue(studyId, callerUserId)) {
            throw new StudyException(ErrorCode.FORBIDDEN, "팀원 목록 조회 권한이 없습니다.");
        }

        List<StudyMember> members = studyMemberRepository.findAllActiveByStudyIdOrderByJoinedAt(studyId);

        List<Long> userIds = members.stream().map(StudyMember::getUserId).toList();
        Map<Long, String> namesByUserId = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdIn(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getName));

        List<MemberSummaryResponse> rows = members.stream()
                .map(m -> new MemberSummaryResponse(
                        m.getId(),
                        m.getUserId(),
                        namesByUserId.getOrDefault(m.getUserId(), ""),
                        m.getRoleCode(),
                        m.getJoinedAt()
                ))
                .toList();

        return new MemberListResponse(rows, rows.size());
    }

    @Transactional
    public void kick(long callerUserId, long studyId, long memberId) {
        Study study = studyRepository.findByIdAndNotDeletedForUpdate(studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        studyMemberRepository.findActiveManager(studyId, callerUserId)
                .orElseThrow(() -> new StudyException(ErrorCode.FORBIDDEN, "멤버 강퇴 권한이 없습니다."));

        StudyMember target = studyMemberRepository.findByIdAndStudyIdForUpdate(memberId, studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "멤버를 찾을 수 없습니다."));

        if (!target.isActive()) {
            throw new StudyException(ErrorCode.NOT_FOUND, "멤버를 찾을 수 없습니다.");
        }
        if (target.isLeader()) {
            throw new StudyException(ErrorCode.CANNOT_REMOVE_LEADER);
        }

        target.kick();
        study.decrementMemberCount();
    }

    @Transactional
    public void leave(long userId, long studyId) {
        Study study = studyRepository.findByIdAndNotDeletedForUpdate(studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember self = studyMemberRepository.findActiveByStudyIdAndUserIdForUpdate(studyId, userId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "활성 멤버가 아닙니다."));

        if (self.isLeader()) {
            throw new StudyException(ErrorCode.CONFLICT, "팀장은 탈퇴할 수 없습니다.");
        }

        self.leave();
        study.decrementMemberCount();
    }
}
