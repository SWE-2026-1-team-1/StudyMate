package com.studymate.study.fixture;

import com.studymate.study.domain.*;
import com.studymate.study.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 스터디 도메인 통합 테스트용 데이터 빌더.
 */
public class StudyFixture {

    private StudyFixture() {}

    /**
     * OPEN study + LEADER member + 태그 + 언어 한 번에 persist.
     *
     * @return studyId
     */
    public static long createOpenStudy(
            long leaderId,
            int maxMembers,
            List<Long> tagIds,
            List<String> languages,
            StudyRepository studyRepo,
            StudyMemberRepository memberRepo,
            StudyTagRepository tagRepo,
            StudyLanguageRepository langRepo
    ) {
        Study study = Study.create("테스트 스터디", "목표", maxMembers, 4, "매주 월");
        study = studyRepo.save(study);
        long studyId = study.getId();

        memberRepo.save(StudyMember.createLeader(studyId, leaderId));

        tagRepo.saveAll(tagIds.stream().map(tid -> StudyTag.of(studyId, tid)).toList());
        langRepo.saveAll(languages.stream().map(l -> StudyLanguage.of(studyId, l)).toList());

        return studyId;
    }

    /** CLOSED study (currentMemberCount=closedWith). JdbcTemplate 으로 직접 status 업데이트. */
    public static long createClosedStudy(
            long leaderId,
            int maxMembers,
            int currentMembers,
            StudyRepository studyRepo,
            StudyMemberRepository memberRepo,
            JdbcTemplate jdbc
    ) {
        Study study = Study.create("마감 스터디", "목표", maxMembers, 4, "매주 화");
        study = studyRepo.save(study);
        long studyId = study.getId();

        memberRepo.save(StudyMember.createLeader(studyId, leaderId));

        // currentMemberCount 와 status 를 직접 설정
        jdbc.update("UPDATE study SET status='CLOSED', current_member_count=? WHERE id=?",
                currentMembers, studyId);

        return studyId;
    }

    /** 단순 OPEN study (태그/언어 없음). */
    public static long createSimpleStudy(
            long leaderId,
            int maxMembers,
            StudyRepository studyRepo,
            StudyMemberRepository memberRepo
    ) {
        Study study = Study.create("단순 스터디", "목표", maxMembers, 4, "자유");
        study = studyRepo.save(study);
        long studyId = study.getId();
        memberRepo.save(StudyMember.createLeader(studyId, leaderId));
        return studyId;
    }

    /** soft-deleted study. */
    public static long createDeletedStudy(
            long leaderId,
            StudyRepository studyRepo,
            StudyMemberRepository memberRepo,
            JdbcTemplate jdbc
    ) {
        long studyId = createSimpleStudy(leaderId, 5, studyRepo, memberRepo);
        jdbc.update("UPDATE study SET is_deleted=1 WHERE id=?", studyId);
        return studyId;
    }
}
