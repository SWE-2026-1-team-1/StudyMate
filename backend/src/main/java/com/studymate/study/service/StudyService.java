package com.studymate.study.service;

import com.studymate.auth.repository.UserRepository;
import com.studymate.common.exception.ErrorCode;
import com.studymate.study.command.StudyCreateCommand;
import com.studymate.study.command.StudyUpdateCommand;
import com.studymate.study.domain.*;
import com.studymate.study.dto.response.*;
import com.studymate.study.exception.StudyException;
import com.studymate.study.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyService {

    private final StudyRepository          studyRepository;
    private final StudyMemberRepository    studyMemberRepository;
    private final StudyTagRepository       studyTagRepository;
    private final StudyLanguageRepository  studyLanguageRepository;
    private final TagRepository            tagRepository;
    private final UserRepository           userRepository;

    @Transactional
    public StudyCreateResponse create(long userId, StudyCreateCommand command) {
        // 태그 findOrCreate
        List<Tag> tags = findOrCreateTags(deduplicateIgnoreCase(command.tags()));

        Study study = Study.create(
                command.title(),
                command.description(),
                command.maxMembers(),
                command.durationWeeks(),
                command.meetingCycle()
        );
        study = studyRepository.save(study);

        // LEADER row (D-012)
        studyMemberRepository.save(StudyMember.createLeader(study.getId(), userId));

        // 태그 연결
        long studyId = study.getId();
        List<StudyTag> studyTags = tags.stream()
                .map(t -> StudyTag.of(studyId, t.getId()))
                .toList();
        studyTagRepository.saveAll(studyTags);

        // 언어 연결
        List<String> langs = deduplicateIgnoreCase(command.languages());
        List<StudyLanguage> studyLangs = langs.stream()
                .map(l -> StudyLanguage.of(studyId, l))
                .toList();
        studyLanguageRepository.saveAll(studyLangs);

        return new StudyCreateResponse(
                study.getId(),
                study.getTitle(),
                study.getStatus(),
                study.getCurrentMemberCount(),
                study.getMaxMembers()
        );
    }

    @Transactional(readOnly = true)
    public StudyListResponse list(int page, int size) {
        Page<Study> studyPage = studyRepository.findAllOpenNotDeleted(PageRequest.of(page, size));
        List<Study> studies = studyPage.getContent();

        // batch fetch tags
        List<Long> studyIds = studies.stream().map(Study::getId).toList();
        Map<Long, List<String>> tagsByStudy = studyIds.isEmpty()
                ? Map.of()
                : studyTagRepository.findAllByStudyIdIn(studyIds).stream()
                    .collect(Collectors.groupingBy(
                            StudyTag::getStudyId,
                            Collectors.mapping(st -> resolveTagName(st.getTagId()), Collectors.toList())
                    ));

        List<StudySummaryResponse> summaries = studies.stream()
                .map(s -> new StudySummaryResponse(
                        s.getId(),
                        s.getTitle(),
                        tagsByStudy.getOrDefault(s.getId(), List.of()),
                        s.getStatus(),
                        s.getCurrentMemberCount(),
                        s.getMaxMembers()
                ))
                .toList();

        return new StudyListResponse(summaries, studyPage.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public StudyDetailResponse detail(long studyId) {
        Study study = studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        List<String> tags = studyTagRepository.findAllByIdStudyId(studyId).stream()
                .map(st -> resolveTagName(st.getTagId()))
                .toList();

        List<String> langs = studyLanguageRepository.findAllByIdStudyId(studyId).stream()
                .map(StudyLanguage::getLanguageCode)
                .toList();

        StudyMember leader = studyMemberRepository.findActiveLeader(studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "스터디장을 찾을 수 없습니다."));

        String leaderName = userRepository.findById(leader.getUserId())
                .map(u -> u.getName())
                .orElse("");

        return new StudyDetailResponse(
                study.getId(),
                study.getTitle(),
                study.getPurpose(),
                tags,
                langs,
                study.getMaxMembers(),
                study.getCurrentMemberCount(),
                study.getActivityCycle(),
                study.getDurationWeeks(),
                study.getStatus(),
                new StudyDetailResponse.CreatedBy(leader.getUserId(), leaderName),
                study.getCreatedAt()
        );
    }

    @Transactional
    public StudyUpdateResponse update(long userId, long studyId, StudyUpdateCommand command) {
        Study study = studyRepository.findByIdAndIsDeletedFalse(studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        assertLeader(studyId, userId);

        // 값이 존재할 때 기본 검증 (DTO 에서 제약 제거한 대신 서비스 레이어에서 처리)
        validateUpdateFields(command);

        // 분기 1: status 변경
        if (command.status().isPresent()) {
            StudyStatus target = command.status().get();
            assertValidStatusTransition(study, target);
            study.changeStatus(target);
        }

        // 분기 2: CLOSED 상태에서 금지 필드 수정 시도
        if (study.getStatus() == StudyStatus.CLOSED) {
            boolean hasForbiddenField = command.maxMembers().isPresent()
                    || command.durationWeeks().isPresent()
                    || command.meetingCycle().isPresent();
            if (hasForbiddenField) {
                throw new StudyException(ErrorCode.INVALID_STATUS_FOR_UPDATE);
            }
        }

        // 분기 3: maxMembers 검증 (D-011)
        if (command.maxMembers().isPresent()) {
            int newMax = command.maxMembers().get();
            if (newMax < study.getCurrentMemberCount()) {
                throw new StudyException(ErrorCode.INVALID_MAX_MEMBERS);
            }
            study.changeMaxMembers(newMax);
        }

        // 분기 4: 나머지 일반 필드
        command.title().ifPresent(study::rename);
        command.description().ifPresent(study::changePurpose);
        command.meetingCycle().ifPresent(study::changeActivityCycle);
        command.durationWeeks().ifPresent(study::changeDurationWeeks);

        if (command.tags().isPresent()) {
            List<Tag> newTags = findOrCreateTags(deduplicateIgnoreCase(command.tags().get()));
            studyTagRepository.deleteAllByIdStudyId(studyId);
            studyTagRepository.saveAll(
                    newTags.stream().map(t -> StudyTag.of(studyId, t.getId())).toList()
            );
        }

        if (command.languages().isPresent()) {
            studyLanguageRepository.deleteAllByIdStudyId(studyId);
            studyLanguageRepository.saveAll(
                    deduplicateIgnoreCase(command.languages().get()).stream()
                            .map(l -> StudyLanguage.of(studyId, l))
                            .toList()
            );
        }

        // dirty check 보장을 위해 명시적 저장
        study = studyRepository.save(study);

        return new StudyUpdateResponse(
                study.getId(),
                study.getTitle(),
                study.getStatus(),
                study.getUpdatedAt()
        );
    }

    @Transactional
    public void delete(long userId, long studyId) {
        Study study = studyRepository.findByIdAndNotDeletedForUpdate(studyId)
                .orElseThrow(() -> new StudyException(ErrorCode.NOT_FOUND, "스터디를 찾을 수 없습니다."));

        assertLeader(studyId, userId);
        study.markDeleted();
        studyRepository.save(study);
    }

    // ─── private helpers ─────────────────────────────────────────────────

    private void validateUpdateFields(StudyUpdateCommand cmd) {
        cmd.title().ifPresent(v -> {
            if (v == null || v.isBlank() || v.length() < 2 || v.length() > 50)
                throw new StudyException(ErrorCode.INVALID_INPUT, "제목은 2~50자여야 합니다.");
        });
        cmd.description().ifPresent(v -> {
            if (v == null || v.isBlank() || v.length() > 2000)
                throw new StudyException(ErrorCode.INVALID_INPUT, "설명은 1~2000자여야 합니다.");
        });
        cmd.meetingCycle().ifPresent(v -> {
            if (v == null || v.isBlank() || v.length() > 50)
                throw new StudyException(ErrorCode.INVALID_INPUT, "활동 주기는 1~50자여야 합니다.");
        });
        cmd.maxMembers().ifPresent(v -> {
            if (v == null || v < 2 || v > 50)
                throw new StudyException(ErrorCode.INVALID_INPUT, "최대 멤버는 2~50이어야 합니다.");
        });
        cmd.durationWeeks().ifPresent(v -> {
            if (v == null || v < 1 || v > 52)
                throw new StudyException(ErrorCode.INVALID_INPUT, "활동 기간은 1~52주여야 합니다.");
        });
        cmd.tags().ifPresent(tags -> {
            if (tags == null || tags.isEmpty() || tags.size() > 10)
                throw new StudyException(ErrorCode.INVALID_INPUT, "태그는 1~10개여야 합니다.");
            tags.forEach(t -> {
                if (t == null || t.isBlank() || t.length() > 30)
                    throw new StudyException(ErrorCode.INVALID_INPUT, "태그명은 1~30자여야 합니다.");
            });
        });
        cmd.languages().ifPresent(langs -> {
            if (langs == null || langs.isEmpty() || langs.size() > 10)
                throw new StudyException(ErrorCode.INVALID_INPUT, "언어는 1~10개여야 합니다.");
        });
    }

    private void assertLeader(long studyId, long userId) {
        boolean isLeader = studyMemberRepository
                .existsByStudyIdAndUserIdAndRoleCodeAndIsActiveTrue(studyId, userId, "LEADER");
        if (!isLeader) {
            throw new StudyException(ErrorCode.FORBIDDEN, "스터디 수정 권한이 없습니다.");
        }
    }

    private void assertValidStatusTransition(Study study, StudyStatus target) {
        if (target == StudyStatus.CANCELLED) {
            // CANCELLED 는 수동 전환 불가 (D-015: 미사용)
            throw new StudyException(ErrorCode.INVALID_INPUT, "유효하지 않은 상태값입니다.");
        }
        if (study.getStatus() == StudyStatus.CLOSED && target == StudyStatus.OPEN) {
            // 재오픈: 정원 도달 시 차단
            if (study.getCurrentMemberCount() >= study.getMaxMembers()) {
                throw new StudyException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
        }
    }

    private List<Tag> findOrCreateTags(List<String> names) {
        List<Tag> existing = tagRepository.findAllByNameIn(names);
        Map<String, Tag> existingMap = existing.stream()
                .collect(Collectors.toMap(Tag::getName, t -> t));

        List<Tag> toCreate = names.stream()
                .filter(n -> !existingMap.containsKey(n))
                .map(Tag::create)
                .toList();

        if (!toCreate.isEmpty()) {
            tagRepository.saveAll(toCreate).forEach(t -> existingMap.put(t.getName(), t));
        }

        return names.stream().map(existingMap::get).toList();
    }

    private String resolveTagName(long tagId) {
        return tagRepository.findById(tagId).map(Tag::getName).orElse("");
    }

    private List<String> deduplicateIgnoreCase(List<String> input) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String s : input) {
            if (seen.add(s.toLowerCase(java.util.Locale.ROOT))) {
                result.add(s);
            }
        }
        return result;
    }
}
