package com.studymate.study.integration;

import com.studymate.study.domain.Tag;
import com.studymate.study.fixture.TagFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudyCreateIntegrationTest extends StudyIntegrationTestBase {

    // T-STUDY-CREATE-01: 정상 생성
    @Test
    void T_STUDY_CREATE_01_정상_생성() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        Map<String, Object> body = Map.of(
                "title", "자바 스터디",
                "description", "자바를 공부합니다",
                "tags", List.of("Java", "Spring"),
                "languages", List.of("ko", "en"),
                "maxMembers", 5,
                "durationWeeks", 8,
                "meetingCycle", "매주 월"
        );

        var res = postStudy(token, body);
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> resp = responseBody(res);
        assertThat(resp.get("status")).isEqualTo("OPEN");
        assertThat((Integer) resp.get("currentMembers")).isEqualTo(1);
        assertThat((Integer) resp.get("maxMembers")).isEqualTo(5);

        long studyId = ((Number) resp.get("studyId")).longValue();

        // DB 상태 어서션
        var studyEntity = queryStudy(studyId);
        assertThat(studyEntity.getStatus().name()).isEqualTo("OPEN");
        assertThat(studyEntity.isDeleted()).isFalse();
        assertThat(studyEntity.getCurrentMemberCount()).isEqualTo(1);
        assertThat(studyEntity.getDurationWeeks()).isEqualTo(8);

        // study_member LEADER row
        assertThat(studyMemberRepository.existsByStudyIdAndUserIdAndRoleCodeAndIsActiveTrue(
                studyId, leaderId, "LEADER")).isTrue();

        // tag rows
        assertThat(studyTagRepository.findAllByIdStudyId(studyId)).hasSize(2);

        // language rows
        assertThat(studyLanguageRepository.findAllByIdStudyId(studyId)).hasSize(2);
    }

    // T-STUDY-CREATE-02: 기존 태그 재사용
    @Test
    void T_STUDY_CREATE_02_기존_태그_재사용() throws Exception {
        TagFixture.persist("Java", tagRepository);
        long tagCountBefore = tagRepository.count();

        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        Map<String, Object> body = Map.of(
                "title", "자바 스터디", "description", "설명",
                "tags", List.of("Java", "Spring"),
                "languages", List.of("ko"),
                "maxMembers", 5, "durationWeeks", 4, "meetingCycle", "매주"
        );

        postStudy(token, body);

        // 신규 tag = Spring 1건만
        assertThat(tagRepository.count()).isEqualTo(tagCountBefore + 1);
    }

    // T-STUDY-CREATE-03: 중복 태그 제거
    @Test
    void T_STUDY_CREATE_03_중복_태그_제거() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        String token = bearerToken(leaderId);

        Map<String, Object> body = Map.of(
                "title", "스터디", "description", "설명",
                "tags", List.of("Java", "java", "Spring"),
                "languages", List.of("ko"),
                "maxMembers", 5, "durationWeeks", 4, "meetingCycle", "매주"
        );

        var res = postStudy(token, body);
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        long studyId = ((Number) responseBody(res).get("studyId")).longValue();
        // java/Java 중복 제거 → study_tag 2건 (Java + Spring)
        assertThat(studyTagRepository.findAllByIdStudyId(studyId)).hasSize(2);
    }

    // T-STUDY-CREATE-04: 인증 없음 → 401
    @Test
    void T_STUDY_CREATE_04_인증_없음() throws Exception {
        var res = postStudy("", Map.of(
                "title", "스터디", "description", "설명",
                "tags", List.of("Java"), "languages", List.of("ko"),
                "maxMembers", 5, "durationWeeks", 4, "meetingCycle", "매주"
        ));
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(studyRepository.count()).isZero();
    }
}
