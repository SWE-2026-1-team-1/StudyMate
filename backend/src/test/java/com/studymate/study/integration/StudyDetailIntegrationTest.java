package com.studymate.study.integration;

import com.studymate.study.domain.Tag;
import com.studymate.study.fixture.StudyFixture;
import com.studymate.study.fixture.TagFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudyDetailIntegrationTest extends StudyIntegrationTestBase {

    // T-STUDY-DETAIL-01: 상세 조회 응답 형식 검증
    @Test
    void T_STUDY_DETAIL_01_상세_조회() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        Tag java = TagFixture.persist("Java", tagRepository);
        Tag spring = TagFixture.persist("Spring", tagRepository);

        long studyId = StudyFixture.createOpenStudy(
                leaderId, 5,
                List.of(java.getId(), spring.getId()),
                List.of("ko", "en"),
                studyRepository, studyMemberRepository, studyTagRepository, studyLanguageRepository
        );

        var res = getStudy(token, studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> body = responseBody(res);
        assertThat(body.get("studyId")).isNotNull();
        assertThat(body.get("title")).isNotNull();
        assertThat((List<?>) body.get("tags")).hasSize(2);
        assertThat((List<?>) body.get("languages")).hasSize(2);
        assertThat(body.get("status")).isEqualTo("OPEN");

        Map<?, ?> createdBy = (Map<?, ?>) body.get("createdBy");
        assertThat(createdBy.get("userId")).isNotNull();
        assertThat(createdBy.get("name")).isEqualTo(TEST_NAME);
    }

    // T-STUDY-DETAIL-02: 미존재 studyId → 404
    @Test
    void T_STUDY_DETAIL_02_미존재() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        var res = getStudy(bearerToken(leaderId), 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("NOT_FOUND");
    }

    // T-STUDY-DETAIL-03: soft-deleted → 404
    @Test
    void T_STUDY_DETAIL_03_soft_delete_비노출() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createDeletedStudy(
                leaderId, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = getStudy(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-STUDY-DETAIL-04: CLOSED study → 200 (상세는 노출)
    @Test
    void T_STUDY_DETAIL_04_CLOSED_상세노출() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createClosedStudy(
                leaderId, 5, 2, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = getStudy(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(responseBody(res).get("status")).isEqualTo("CLOSED");
    }

    // T-STUDY-DETAIL-05: 인증 없음 → 401
    @Test
    void T_STUDY_DETAIL_05_인증_없음() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);

        var res = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/studies/" + studyId)
        ).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
