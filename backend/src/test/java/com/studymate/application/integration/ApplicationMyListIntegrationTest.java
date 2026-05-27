package com.studymate.application.integration;

import com.studymate.application.fixture.ApplicationFixture;
import com.studymate.application.fixture.StudyMemberFixture;
import com.studymate.study.domain.Study;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationMyListIntegrationTest extends ApplicationIntegrationTestBase {

    private long createOpenStudy(long leaderId, String title) {
        Study study = studyRepository.save(Study.create(title, "설명", 5, 4, "주1회"));
        StudyMemberFixture.leader(study.getId(), leaderId, studyMemberRepository);
        return study.getId();
    }

    // T-APP-MYLIST-01
    @Test
    @SuppressWarnings("unchecked")
    void T_APP_MYLIST_01_본인_신청_DESC() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long s1 = createOpenStudy(leaderId, "S1");
        long s2 = createOpenStudy(leaderId, "S2");
        long s3 = createOpenStudy(leaderId, "S3");
        long leaderMember1 = studyMemberRepository.findActiveLeader(s1).orElseThrow().getId();

        ApplicationFixture.pending(s1, applicantId, "m1", applicationRepository);
        ApplicationFixture.accepted(s2, applicantId, leaderMember1, applicationRepository, jdbcTemplate);
        ApplicationFixture.rejected(s3, applicantId, leaderMember1, applicationRepository, jdbcTemplate);

        var res = getMyApplications(bearerToken(applicantId));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> resp = responseBody(res);
        assertThat((Number) resp.get("totalCount")).extracting(Number::longValue).isEqualTo(3L);
        List<Map<String, Object>> apps = (List<Map<String, Object>>) resp.get("applications");
        assertThat(apps).hasSize(3);
        // DESC: 마지막에 INSERT 된 게 첫번째
        assertThat(apps.get(0).get("studyTitle")).isEqualTo("S3");
    }

    // T-APP-MYLIST-02
    @Test
    void T_APP_MYLIST_02_빈_결과() throws Exception {
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        var res = getMyApplications(bearerToken(applicantId));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> resp = responseBody(res);
        assertThat((Number) resp.get("totalCount")).extracting(Number::longValue).isEqualTo(0L);
    }

    // T-APP-MYLIST-03
    @Test
    @SuppressWarnings("unchecked")
    void T_APP_MYLIST_03_본인것만_노출() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long meId = saveUser(APPLICANT_EMAIL, "본인");
        long otherId = saveUser(OTHER_EMAIL, "타인");
        long s1 = createOpenStudy(leaderId, "S1");

        ApplicationFixture.pending(s1, meId, "내거", applicationRepository);
        ApplicationFixture.pending(s1, otherId, "남거", applicationRepository);

        var res = getMyApplications(bearerToken(meId));
        Map<String, Object> resp = responseBody(res);
        assertThat((Number) resp.get("totalCount")).extracting(Number::longValue).isEqualTo(1L);
        List<Map<String, Object>> apps = (List<Map<String, Object>>) resp.get("applications");
        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).get("studyTitle")).isEqualTo("S1");
    }

    // T-APP-MYLIST-04
    @Test
    void T_APP_MYLIST_04_soft_deleted_study_포함() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long meId = saveUser(APPLICANT_EMAIL, "본인");
        long s1 = createOpenStudy(leaderId, "삭제될스터디");
        ApplicationFixture.pending(s1, meId, "msg", applicationRepository);
        jdbcTemplate.update("UPDATE study SET is_deleted=1 WHERE id=?", s1);

        var res = getMyApplications(bearerToken(meId));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> resp = responseBody(res);
        assertThat((Number) resp.get("totalCount")).extracting(Number::longValue).isEqualTo(1L);
    }

    // T-APP-MYLIST-06
    @Test
    void T_APP_MYLIST_06_인증_없음() throws Exception {
        var res = getMyApplications("");
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
