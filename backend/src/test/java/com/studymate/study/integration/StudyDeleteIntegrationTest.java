package com.studymate.study.integration;

import com.studymate.study.fixture.StudyFixture;
import com.studymate.study.fixture.TagFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyDeleteIntegrationTest extends StudyIntegrationTestBase {

    // T-STUDY-DELETE-01: LEADER 가 OPEN 스터디 삭제 → 자식 row 보존
    @Test
    void T_STUDY_DELETE_01_OPEN_삭제_자식보존() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        var java = TagFixture.persist("Java", tagRepository);

        long studyId = StudyFixture.createOpenStudy(
                leaderId, 5,
                List.of(java.getId()),
                List.of("ko"),
                studyRepository, studyMemberRepository, studyTagRepository, studyLanguageRepository
        );

        var res = deleteStudy(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);

        // is_deleted = 1
        assertThat(queryStudy(studyId).isDeleted()).isTrue();

        // 자식 row 보존
        assertThat(studyMemberRepository.findByStudyIdAndUserIdAndIsActiveTrue(studyId, leaderId)).isPresent();
        assertThat(studyTagRepository.findAllByIdStudyId(studyId)).hasSize(1);
        assertThat(studyLanguageRepository.findAllByIdStudyId(studyId)).hasSize(1);

        // 이후 GET → 404
        var getRes = getStudy(bearerToken(leaderId), studyId);
        assertThat(getRes.getResponse().getStatus()).isEqualTo(404);
    }

    // T-STUDY-DELETE-02: CLOSED 스터디 삭제
    @Test
    void T_STUDY_DELETE_02_CLOSED_삭제() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createClosedStudy(leaderId, 5, 1, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = deleteStudy(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        assertThat(queryStudy(studyId).isDeleted()).isTrue();
    }

    // T-STUDY-DELETE-03: 비-LEADER 삭제 → 403
    @Test
    void T_STUDY_DELETE_03_비리더_삭제불가() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long otherId  = saveUser(TEST_EMAIL2, TEST_NAME2);
        long studyId  = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);

        var res = deleteStudy(bearerToken(otherId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(queryStudy(studyId).isDeleted()).isFalse();
    }

    // T-STUDY-DELETE-04: 미존재 → 404
    @Test
    void T_STUDY_DELETE_04_미존재() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        var res = deleteStudy(bearerToken(leaderId), 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-STUDY-DELETE-05: 이미 삭제된 스터디 → 404
    @Test
    void T_STUDY_DELETE_05_이미_삭제() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createDeletedStudy(leaderId, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = deleteStudy(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-STUDY-DELETE-06: 인증 없음 → 401
    @Test
    void T_STUDY_DELETE_06_인증_없음() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);

        var res = deleteStudy("", studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // T-STUDY-DELETE-07: 삭제 후 목록에서 제외
    @Test
    void T_STUDY_DELETE_07_삭제후_목록제외() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);
        deleteStudy(bearerToken(leaderId), studyId);

        var res = getStudies(bearerToken(leaderId));
        var studies = (java.util.List<?>) responseBody(res).get("studies");
        assertThat(studies).isEmpty();
    }
}
