package com.studymate.application.integration;

import com.studymate.application.fixture.ApplicationFixture;
import com.studymate.application.fixture.StudyMemberFixture;
import com.studymate.study.domain.Study;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRejectIntegrationTest extends ApplicationIntegrationTestBase {

    private long createOpenStudy(long leaderId) {
        Study study = studyRepository.save(Study.create("스터디", "설명", 5, 4, "주1회"));
        StudyMemberFixture.leader(study.getId(), leaderId, studyMemberRepository);
        return study.getId();
    }

    // T-APP-REJECT-01
    @Test
    void T_APP_REJECT_01_정상_거절_사유포함() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postReject(bearerToken(leaderId), studyId, appId,
                Map.of("rejectReason", "방향 불일치"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> resp = responseBody(res);
        assertThat(resp.get("status")).isEqualTo("REJECTED");
        assertThat(resp.get("rejectReason")).isEqualTo("방향 불일치");

        assertThat(queryApplicationStatus(appId)).isEqualTo("REJECTED");
        assertThat(activeMemberExists(studyId, applicantId)).isFalse();
        assertThat(queryStudy(studyId).getCurrentMemberCount()).isEqualTo(1);
    }

    // T-APP-REJECT-02
    @Test
    void T_APP_REJECT_02_본문_없음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postReject(bearerToken(leaderId), studyId, appId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryApplicationStatus(appId)).isEqualTo("REJECTED");
    }

    // T-APP-REJECT-03
    @Test
    void T_APP_REJECT_03_빈_본문() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postReject(bearerToken(leaderId), studyId, appId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    // T-APP-REJECT-04
    @Test
    void T_APP_REJECT_04_이미_ACCEPTED() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long leaderMemberId = studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();
        long appId = ApplicationFixture.accepted(studyId, applicantId, leaderMemberId,
                applicationRepository, jdbcTemplate);

        var res = postReject(bearerToken(leaderId), studyId, appId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVALID_APPLICATION_STATUS");
    }

    // T-APP-REJECT-06
    @Test
    void T_APP_REJECT_06_비_LEADER_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(OTHER_EMAIL, "멤버");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        StudyMemberFixture.member(studyId, memberId, studyMemberRepository);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postReject(bearerToken(memberId), studyId, appId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    // T-APP-REJECT-07
    @Test
    void T_APP_REJECT_07_미존재_applicationId() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId);
        var res = postReject(bearerToken(leaderId), studyId, 9999L, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-REJECT-08
    @Test
    void T_APP_REJECT_08_studyId_불일치() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyA = createOpenStudy(leaderId);
        long studyB = createOpenStudy(leaderId);
        long appId = ApplicationFixture.pending(studyA, applicantId, null, applicationRepository);

        var res = postReject(bearerToken(leaderId), studyB, appId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-REJECT-10
    @Test
    void T_APP_REJECT_10_인증_없음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        var res = postReject("", studyId, appId, null);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // T-APP-REJECT-11
    @Test
    void T_APP_REJECT_11_사유_501자() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId);
        long appId = ApplicationFixture.pending(studyId, applicantId, null, applicationRepository);

        Map<String, Object> body = new HashMap<>();
        body.put("rejectReason", "a".repeat(501));

        var res = postReject(bearerToken(leaderId), studyId, appId, body);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }
}
