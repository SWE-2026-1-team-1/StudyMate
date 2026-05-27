package com.studymate.application.service;

import com.studymate.application.command.ApplicationCreateCommand;
import com.studymate.application.command.ApplicationRejectCommand;
import com.studymate.application.domain.Application;
import com.studymate.application.domain.ApplicationStatus;
import com.studymate.application.dto.response.*;
import com.studymate.application.exception.ApplicationException;
import com.studymate.application.repository.ApplicationRepository;
import com.studymate.auth.domain.User;
import com.studymate.auth.repository.UserRepository;
import com.studymate.common.exception.ErrorCode;
import com.studymate.study.domain.Study;
import com.studymate.study.domain.StudyMember;
import com.studymate.study.domain.StudyStatus;
import com.studymate.study.repository.StudyMemberRepository;
import com.studymate.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final StudyRepository       studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final UserRepository        userRepository;

    @Transactional
    public ApplicationCreateResponse apply(long userId, long studyId, ApplicationCreateCommand command) {
        Study study = studyRepository.findByIdAndNotDeletedForUpdate(studyId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        if (study.getStatus() != StudyStatus.OPEN) {
            throw new ApplicationException(ErrorCode.STUDY_FULL);
        }
        if (studyMemberRepository.existsByStudyIdAndUserIdAndIsActiveTrue(studyId, userId)) {
            throw new ApplicationException(ErrorCode.ALREADY_MEMBER);
        }
        if (applicationRepository.findPending(studyId, userId).isPresent()) {
            throw new ApplicationException(ErrorCode.ALREADY_APPLIED);
        }

        Application saved = applicationRepository.save(
                Application.createPending(studyId, userId, command.message())
        );

        return new ApplicationCreateResponse(
                saved.getId(),
                saved.getStudyId(),
                saved.getStatus(),
                saved.getAppliedAt()
        );
    }

    @Transactional
    public void cancelMine(long userId, long studyId) {
        studyRepository.findByIdAndNotDeletedForUpdate(studyId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        Application pending = applicationRepository.findPendingForUpdate(studyId, userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "취소할 신청을 찾을 수 없습니다."));

        applicationRepository.delete(pending);
    }

    @Transactional(readOnly = true)
    public ApplicationListResponse list(long callerUserId, long studyId, int page, int size) {
        studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        studyMemberRepository.findActiveApprover(studyId, callerUserId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FORBIDDEN, "지원자 목록 조회 권한이 없습니다."));

        Page<Application> appPage = applicationRepository
                .findAllPendingByStudyId(studyId, PageRequest.of(page, size));

        List<Long> applicantIds = appPage.getContent().stream()
                .map(Application::getApplicantId)
                .toList();
        Map<Long, String> namesByUserId = applicantIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdIn(applicantIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getName));

        List<ApplicationSummaryResponse> summaries = appPage.getContent().stream()
                .map(a -> new ApplicationSummaryResponse(
                        a.getId(),
                        a.getApplicantId(),
                        namesByUserId.getOrDefault(a.getApplicantId(), ""),
                        a.getMessage(),
                        a.getAppliedAt()
                ))
                .toList();

        return new ApplicationListResponse(summaries, appPage.getTotalElements(), page, size);
    }

    @Transactional
    public ApplicationApproveResponse approve(long callerUserId, long studyId, long applicationId) {
        Study study = studyRepository.findByIdAndNotDeletedForUpdate(studyId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember approver = studyMemberRepository.findActiveApprover(studyId, callerUserId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FORBIDDEN, "신청 처리 권한이 없습니다."));

        Application application = applicationRepository.findByIdAndStudyIdForUpdate(applicationId, studyId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다."));

        if (!application.isPending()) {
            throw new ApplicationException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        if (study.getStatus() != StudyStatus.OPEN) {
            throw new ApplicationException(ErrorCode.STUDY_FULL);
        }

        application.accept(approver.getId());
        studyMemberRepository.save(StudyMember.createMember(studyId, application.getApplicantId()));
        study.incrementMemberCount();
        if (study.getCurrentMemberCount() >= study.getMaxMembers()) {
            study.changeStatus(StudyStatus.CLOSED);
        }
        studyRepository.save(study);

        return new ApplicationApproveResponse(
                application.getId(),
                application.getStatus(),
                application.getProcessedAt(),
                study.getStatus()
        );
    }

    @Transactional
    public ApplicationRejectResponse reject(long callerUserId, long studyId, long applicationId,
                                            ApplicationRejectCommand command) {
        studyRepository.findByIdAndNotDeletedForUpdate(studyId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        StudyMember approver = studyMemberRepository.findActiveApprover(studyId, callerUserId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FORBIDDEN, "신청 처리 권한이 없습니다."));

        Application application = applicationRepository.findByIdAndStudyIdForUpdate(applicationId, studyId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다."));

        if (!application.isPending()) {
            throw new ApplicationException(ErrorCode.INVALID_APPLICATION_STATUS);
        }

        String reason = command == null ? null : command.rejectReason();
        application.reject(approver.getId(), reason);

        return new ApplicationRejectResponse(
                application.getId(),
                application.getStatus(),
                application.getProcessedAt(),
                application.getRejectReason()
        );
    }

    @Transactional(readOnly = true)
    public MyApplicationListResponse listMine(long userId, int page, int size) {
        Page<Application> appPage = applicationRepository
                .findAllByApplicantIdOrderByAppliedAtDesc(userId, PageRequest.of(page, size));

        List<Long> studyIds = appPage.getContent().stream()
                .map(Application::getStudyId)
                .toList();
        Map<Long, Study> studyByIds = studyIds.isEmpty()
                ? Map.of()
                : studyRepository.findAllByIdIn(studyIds).stream()
                    .collect(Collectors.toMap(Study::getId, Function.identity()));

        List<MyApplicationResponse> rows = appPage.getContent().stream()
                .map(a -> {
                    Study s = studyByIds.get(a.getStudyId());
                    String title = s == null ? "" : s.getTitle();
                    StudyStatus status = s == null ? null : s.getStatus();
                    return new MyApplicationResponse(
                            a.getId(),
                            a.getStudyId(),
                            title,
                            status,
                            a.getStatus(),
                            a.getAppliedAt(),
                            a.getProcessedAt()
                    );
                })
                .toList();

        return new MyApplicationListResponse(rows, appPage.getTotalElements(), page, size);
    }
}
