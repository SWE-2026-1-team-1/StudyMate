package com.studymate.application.integration;

import com.studymate.application.fixture.ApplicationFixture;
import com.studymate.application.fixture.StudyMemberFixture;
import com.studymate.study.domain.Study;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationCancelIntegrationTest extends ApplicationIntegrationTestBase {

    private long createOpenStudy(long leaderId) {
        Study study = studyRepository.save(Study.create("스터디", "설명", 5, 4, "주1회"));
        StudyMemberFixture.leader(study.getId(), leaderId, studyMemberRepository);
        return study.getId();
    }

    // T-APP-CANCEL-01
    @Test
    void T_APP_CANCEL_01_PENDING_삭제() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = deleteApplyMine(bearerToken(applicantId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        assertThat(applicationExists(appId)).isFalse();
    }

    // T-APP-CANCEL-02
    @Test
    void T_APP_CANCEL_02_PENDING_없음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);

        var res = deleteApplyMine(bearerToken(applicantId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-CANCEL-03
    @Test
    void T_APP_CANCEL_03_ACCEPTED_만_있음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long leaderMemberId = studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();
        long appId = ApplicationFixture.accepted(studyId, applicantId, leaderMemberId,
                applicationRepository, jdbcTemplate);

        var res = deleteApplyMine(bearerToken(applicantId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
        assertThat(applicationExists(appId)).isTrue();
    }

    // T-APP-CANCEL-04
    @Test
    void T_APP_CANCEL_04_REJECTED_만_있음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long leaderMemberId = studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();
        long appId = ApplicationFixture.rejected(studyId, applicantId, leaderMemberId,
                applicationRepository, jdbcTemplate);

        var res = deleteApplyMine(bearerToken(applicantId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
        assertThat(applicationExists(appId)).isTrue();
    }

    // T-APP-CANCEL-05
    @Test
    void T_APP_CANCEL_05_미존재_studyId() throws Exception {
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        var res = deleteApplyMine(bearerToken(applicantId), 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-CANCEL-06
    @Test
    void T_APP_CANCEL_06_soft_deleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        jdbcTemplate.update("UPDATE study SET is_deleted=1 WHERE id=?", studyId);

        var res = deleteApplyMine(bearerToken(applicantId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-CANCEL-07
    @Test
    void T_APP_CANCEL_07_인증_없음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId);
        var res = deleteApplyMine("", studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
