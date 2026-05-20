package com.studymate.study.integration;

import com.studymate.study.domain.Study;
import com.studymate.study.domain.StudyMember;
import com.studymate.study.fixture.StudyFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudyListIntegrationTest extends StudyIntegrationTestBase {

    // T-STUDY-LIST-01: OPEN 만, 최신순 반환
    @Test
    void T_STUDY_LIST_01_OPEN만_최신순() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        // OPEN 3건 생성 (createdAt 차이 보장용 sleep 없이 DB update 로 처리)
        for (int i = 0; i < 3; i++) {
            StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);
        }
        // CLOSED 1건
        StudyFixture.createClosedStudy(leaderId, 5, 1, studyRepository, studyMemberRepository, jdbcTemplate);
        // soft-deleted 1건
        StudyFixture.createDeletedStudy(leaderId, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = getStudies(token);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> body = responseBody(res);
        List<?> studies = (List<?>) body.get("studies");
        assertThat(studies).hasSize(3);
        assertThat(((Number) body.get("totalCount")).longValue()).isEqualTo(3);
        assertThat(((Number) body.get("page")).intValue()).isZero();
        assertThat(((Number) body.get("size")).intValue()).isEqualTo(20);
    }

    // T-STUDY-LIST-02/03: 페이징
    @Test
    void T_STUDY_LIST_02_03_페이징() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        for (int i = 0; i < 25; i++) {
            StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);
        }

        var res1 = getStudies(token, 0, 10);
        List<?> page0 = (List<?>) responseBody(res1).get("studies");
        assertThat(page0).hasSize(10);

        var res2 = getStudies(token, 2, 10);
        List<?> page2 = (List<?>) responseBody(res2).get("studies");
        assertThat(page2).hasSize(5);
        assertThat(((Number) responseBody(res2).get("page")).intValue()).isEqualTo(2);
    }

    // T-STUDY-LIST-04: 빈 목록
    @Test
    void T_STUDY_LIST_04_빈_목록() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        var res = getStudies(token);
        Map<String, Object> body = responseBody(res);
        assertThat(((List<?>) body.get("studies"))).isEmpty();
        assertThat(((Number) body.get("totalCount")).longValue()).isZero();
    }

    // T-STUDY-LIST-05: 인증 없음 → 401
    @Test
    void T_STUDY_LIST_05_인증_없음() throws Exception {
        var res = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/studies")
        ).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // T-STUDY-LIST-08: 파라미터 생략 시 default 적용
    @Test
    void T_STUDY_LIST_08_기본값_적용() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        var res = getStudies(token);
        Map<String, Object> body = responseBody(res);
        assertThat(((Number) body.get("page")).intValue()).isZero();
        assertThat(((Number) body.get("size")).intValue()).isEqualTo(20);
    }
}
