package com.studymate.application.fixture;

import com.studymate.study.domain.StudyMember;
import com.studymate.study.repository.StudyMemberRepository;

public class StudyMemberFixture {

    private StudyMemberFixture() {}

    public static long leader(long studyId, long userId, StudyMemberRepository repo) {
        return repo.save(StudyMember.createLeader(studyId, userId)).getId();
    }

    public static long member(long studyId, long userId, StudyMemberRepository repo) {
        return repo.save(StudyMember.createMember(studyId, userId)).getId();
    }
}
