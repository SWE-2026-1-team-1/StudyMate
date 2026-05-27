package com.studymate.application.integration;

import com.studymate.application.fixture.ApplicationFixture;
import com.studymate.application.fixture.StudyMemberFixture;
import com.studymate.study.domain.Study;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationListIntegrationTest extends ApplicationIntegrationTestBase {

    private long createOpenStudy(long leaderId) {
        Study study = studyRepository.save(Study.create("스터디", "설명", 5, 4, "주1회"));
        StudyMemberFixture.leader(study.getId(), leaderId, studyMemberRepository);
        return study.getId();
    }

    // T-APP-LIST-01
    @Test
    @SuppressWarnings("unchecked")
    void T_APP_LIST_01_PENDING_만_조회() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long u1 = saveUser("a@university.ac.kr", "A");
        long u2 = saveUser("b@university.ac.kr", "B");
        long u3 = saveUser("c@university.ac.kr", "C");
        long u4 = saveUser("d@university.ac.kr", "D");
        long u5 = saveUser("e@university.ac.kr", "E");
        long studyId = createOpenStudy(leaderId);
        long leaderMemberId = studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();

        ApplicationFixture.pending(studyId, u1, "m1", applicationRepository);
        ApplicationFixture.pending(studyId, u2, "m2", applicationRepository);
        ApplicationFixture.pending(studyId, u3, "m3", applicationRepository);
        ApplicationFixture.accepted(studyId, u4, leaderMemberId, applicationRepository, jdbcTemplate);
        ApplicationFixture.rejected(studyId, u5, leaderMemberId, applicationRepository, jdbcTemplate);

        var res = getTeamApplications(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> resp = responseBody(res);
        assertThat((Number) resp.get("totalCount")).extracting(Number::longValue).isEqualTo(3L);
        List<Map<String, Object>> apps = (List<Map<String, Object>>) resp.get("applications");
        assertThat(apps).hasSize(3);
        assertThat(apps.get(0).get("applicantName")).isEqualTo("A");
    }

    // T-APP-LIST-04
    @Test
    void T_APP_LIST_04_PENDING_0건() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId);

        var res = getTeamApplications(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> resp = responseBody(res);
        assertThat((Number) resp.get("totalCount")).extracting(Number::longValue).isEqualTo(0L);
    }

    // T-APP-LIST-05
    @Test
    void T_APP_LIST_05_비_LEADER_member_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(OTHER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId);
        StudyMemberFixture.member(studyId, memberId, studyMemberRepository);

        var res = getTeamApplications(bearerToken(memberId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("FORBIDDEN");
    }

    // T-APP-LIST-06
    @Test
    void T_APP_LIST_06_비멤버_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long otherId = saveUser(OTHER_EMAIL, "외부인");
        long studyId = createOpenStudy(leaderId);

        var res = getTeamApplications(bearerToken(otherId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    // T-APP-LIST-08
    @Test
    void T_APP_LIST_08_미존재_teamId() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        var res = getTeamApplications(bearerToken(leaderId), 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-LIST-09
    @Test
    void T_APP_LIST_09_soft_deleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId);
        jdbcTemplate.update("UPDATE study SET is_deleted=1 WHERE id=?", studyId);

        var res = getTeamApplications(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-LIST-10
    @Test
    void T_APP_LIST_10_인증_없음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId);
        var res = getTeamApplications("", studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
