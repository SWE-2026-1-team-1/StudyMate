package com.studymate.application.integration;

import com.studymate.application.fixture.ApplicationFixture;
import com.studymate.application.fixture.StudyMemberFixture;
import com.studymate.study.domain.Study;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationApproveIntegrationTest extends ApplicationIntegrationTestBase {

    private long createOpenStudy(long leaderId, int maxMembers, int currentCount) {
        Study study = studyRepository.save(Study.create("스터디", "설명", maxMembers, 4, "주1회"));
        long sid = study.getId();
        StudyMemberFixture.leader(sid, leaderId, studyMemberRepository);
        if (currentCount > 1) {
            jdbcTemplate.update("UPDATE study SET current_member_count=? WHERE id=?",
                    currentCount, sid);
        }
        return sid;
    }

    // T-APP-APPROVE-01
    @Test
    void T_APP_APPROVE_01_정상_수락() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5, 1);
        long appId = ApplicationFixture.pending(studyId, applicantId, "msg", applicationRepository);

        var res = postApprove(bearerToken(leaderId), studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> resp = responseBody(res);
        assertThat(resp.get("status")).isEqualTo("ACCEPTED");
        assertThat(resp.get("studyStatusAfter")).isEqualTo("OPEN");

        assertThat(queryApplicationStatus(appId)).isEqualTo("ACCEPTED");
        assertThat(activeMemberExists(studyId, applicantId)).isTrue();
        assertThat(queryStudy(studyId).getCurrentMemberCount()).isEqualTo(2);
    }

    // T-APP-APPROVE-02
    @Test
    void T_APP_APPROVE_02_자동_마감() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5, 4);
        long appId = ApplicationFixture.pending(studyId, applicantId, "msg", applicationRepository);

        var res = postApprove(bearerToken(leaderId), studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> resp = responseBody(res);
        assertThat(resp.get("studyStatusAfter")).isEqualTo("CLOSED");

        var s = queryStudy(studyId);
        assertThat(s.getCurrentMemberCount()).isEqualTo(5);
        assertThat(s.getStatus().name()).isEqualTo("CLOSED");
    }

    // T-APP-APPROVE-03
    @Test
    void T_APP_APPROVE_03_경계_정확히_max_도달() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 2, 1);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postApprove(bearerToken(leaderId), studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        var s = queryStudy(studyId);
        assertThat(s.getCurrentMemberCount()).isEqualTo(2);
        assertThat(s.getStatus().name()).isEqualTo("CLOSED");
    }

    // T-APP-APPROVE-04
    @Test
    void T_APP_APPROVE_04_이미_ACCEPTED() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5, 1);
        long leaderMemberId = studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();
        long appId = ApplicationFixture.accepted(studyId, applicantId, leaderMemberId,
                applicationRepository, jdbcTemplate);

        var res = postApprove(bearerToken(leaderId), studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVALID_APPLICATION_STATUS");
    }

    // T-APP-APPROVE-05
    @Test
    void T_APP_APPROVE_05_이미_REJECTED() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5, 1);
        long leaderMemberId = studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();
        long appId = ApplicationFixture.rejected(studyId, applicantId, leaderMemberId,
                applicationRepository, jdbcTemplate);

        var res = postApprove(bearerToken(leaderId), studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVALID_APPLICATION_STATUS");
    }

    // T-APP-APPROVE-06
    @Test
    void T_APP_APPROVE_06_CLOSED_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5, 1);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);
        jdbcTemplate.update("UPDATE study SET status='CLOSED' WHERE id=?", studyId);

        var res = postApprove(bearerToken(leaderId), studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("STUDY_FULL");
    }

    // T-APP-APPROVE-07
    @Test
    void T_APP_APPROVE_07_비_LEADER_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(OTHER_EMAIL, "멤버");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5, 1);
        StudyMemberFixture.member(studyId, memberId, studyMemberRepository);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postApprove(bearerToken(memberId), studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    // T-APP-APPROVE-09
    @Test
    void T_APP_APPROVE_09_미존재_applicationId() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5, 1);
        var res = postApprove(bearerToken(leaderId), studyId, 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-APPROVE-10
    @Test
    void T_APP_APPROVE_10_application_studyId_불일치() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyA = createOpenStudy(leaderId, 5, 1);
        long studyB = createOpenStudy(leaderId, 5, 1);
        long appId = ApplicationFixture.pending(studyA, applicantId, null, applicationRepository);

        var res = postApprove(bearerToken(leaderId), studyB, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-APPROVE-13
    @Test
    void T_APP_APPROVE_13_인증_없음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5, 1);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postApprove("", studyId, appId);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
