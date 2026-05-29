package com.studymate.study.integration;

import com.studymate.study.domain.StudyMember;
import com.studymate.study.fixture.StudyFixture;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class MyStudyListIntegrationTest extends StudyIntegrationTestBase {

    private MvcResult getMyStudies(String token) throws Exception {
        return mockMvc.perform(get("/api/studies/my")
                .header("Authorization", token))
                .andReturn();
    }

    // T-MYSTUDY-LIST-01: 본인이 LEADER 인 스터디 노출, role=LEADER
    @Test
    void T_MYSTUDY_LIST_01_LEADER_노출() throws Exception {
        long me = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(me);

        long studyId = StudyFixture.createSimpleStudy(me, 5, studyRepository, studyMemberRepository);

        var res = getMyStudies(token);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studies = (List<Map<String, Object>>) responseBody(res).get("studies");
        assertThat(studies).hasSize(1);
        Map<String, Object> row = studies.get(0);
        assertThat(((Number) row.get("studyId")).longValue()).isEqualTo(studyId);
        assertThat(((Number) row.get("teamId")).longValue()).isEqualTo(studyId);
        assertThat(row.get("role")).isEqualTo("LEADER");
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("title")).isEqualTo("단순 스터디");
        assertThat(row.get("meetingCycle")).isEqualTo("자유");
        assertThat(((Number) row.get("durationWeeks")).intValue()).isEqualTo(4);
        assertThat(((Number) row.get("maxMembers")).intValue()).isEqualTo(5);
    }

    // T-MYSTUDY-LIST-02: MEMBER 로 가입한 스터디 노출, role=MEMBER. 가입 안한 스터디는 미노출.
    @Test
    void T_MYSTUDY_LIST_02_MEMBER_노출_미가입_제외() throws Exception {
        long leader = saveUser(TEST_EMAIL, TEST_NAME);
        long me     = saveUser(TEST_EMAIL2, TEST_NAME2);
        String token = bearerToken(me);

        long joinedId = StudyFixture.createSimpleStudy(leader, 5, studyRepository, studyMemberRepository);
        studyMemberRepository.save(StudyMember.createMember(joinedId, me));

        // 내가 안 들어간 스터디
        StudyFixture.createSimpleStudy(leader, 5, studyRepository, studyMemberRepository);

        var res = getMyStudies(token);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studies = (List<Map<String, Object>>) responseBody(res).get("studies");
        assertThat(studies).hasSize(1);
        assertThat(((Number) studies.get(0).get("studyId")).longValue()).isEqualTo(joinedId);
        assertThat(studies.get(0).get("role")).isEqualTo("MEMBER");
    }

    // T-MYSTUDY-LIST-03: 탈퇴(is_active=0) / 삭제(is_deleted=1) 스터디 제외
    @Test
    void T_MYSTUDY_LIST_03_탈퇴_삭제_제외() throws Exception {
        long leader = saveUser(TEST_EMAIL, TEST_NAME);
        long me     = saveUser(TEST_EMAIL2, TEST_NAME2);
        String token = bearerToken(me);

        // 탈퇴한 스터디
        long leftStudyId = StudyFixture.createSimpleStudy(leader, 5, studyRepository, studyMemberRepository);
        StudyMember member = StudyMember.createMember(leftStudyId, me);
        studyMemberRepository.save(member);
        jdbcTemplate.update("UPDATE study_member SET is_active=0 WHERE study_id=? AND user_id=?",
                leftStudyId, me);

        // soft-deleted 스터디 (내가 활성 멤버여도 노출 X)
        long deletedStudyId = StudyFixture.createSimpleStudy(leader, 5, studyRepository, studyMemberRepository);
        studyMemberRepository.save(StudyMember.createMember(deletedStudyId, me));
        jdbcTemplate.update("UPDATE study SET is_deleted=1 WHERE id=?", deletedStudyId);

        var res = getMyStudies(token);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studies = (List<Map<String, Object>>) responseBody(res).get("studies");
        assertThat(studies).isEmpty();
    }

    // T-MYSTUDY-LIST-04: 인증 없음 → 401
    @Test
    void T_MYSTUDY_LIST_04_인증_없음() throws Exception {
        var res = mockMvc.perform(get("/api/studies/my")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
