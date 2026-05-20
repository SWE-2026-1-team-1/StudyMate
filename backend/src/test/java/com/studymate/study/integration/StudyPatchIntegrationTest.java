package com.studymate.study.integration;

import com.studymate.study.fixture.StudyFixture;
import com.studymate.study.fixture.TagFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudyPatchIntegrationTest extends StudyIntegrationTestBase {

    // T-STUDY-PATCH-01: 일반 필드 수정
    @Test
    void T_STUDY_PATCH_01_타이틀_수정() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("title", "변경된 제목"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryStudy(studyId).getTitle()).isEqualTo("변경된 제목");
    }

    // T-STUDY-PATCH-02: 빈 객체 → no-op
    @Test
    void T_STUDY_PATCH_02_빈_객체_no_op() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);
        String titleBefore = queryStudy(studyId).getTitle();

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryStudy(studyId).getTitle()).isEqualTo(titleBefore);
    }

    // T-STUDY-PATCH-03: null = 변경 없음 (no-op, compact constructor 에서 Optional.empty() 로 변환)
    @Test
    void T_STUDY_PATCH_03_명시적_null은_no_op() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);
        String titleBefore = queryStudy(studyId).getTitle();

        var res = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/studies/" + studyId)
                        .header("Authorization", bearerToken(leaderId))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"title\": null}")
        ).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryStudy(studyId).getTitle()).isEqualTo(titleBefore);
    }

    // T-STUDY-PATCH-04: 비-LEADER → 403
    @Test
    void T_STUDY_PATCH_04_비리더_수정불가() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long otherId  = saveUser(TEST_EMAIL2, TEST_NAME2);
        long studyId  = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);

        var res = patchStudy(bearerToken(otherId), studyId, Map.of("title", "해킹"));
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("FORBIDDEN");
    }

    // T-STUDY-PATCH-05: 미존재 studyId → 404
    @Test
    void T_STUDY_PATCH_05_미존재() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        var res = patchStudy(bearerToken(leaderId), 9999L, Map.of("title", "제목"));
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-STUDY-PATCH-06: soft-deleted → 404
    @Test
    void T_STUDY_PATCH_06_soft_deleted() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createDeletedStudy(leaderId, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("title", "제목"));
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-STUDY-PATCH-07: 인증 없음 → 401
    @Test
    void T_STUDY_PATCH_07_인증_없음() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);

        var res = patchStudy("", studyId, Map.of("title", "제목"));
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // ── status 분기 (D-014) ───────────────────────────────────────────

    // T-STUDY-PATCH-08: OPEN → CLOSED
    @Test
    void T_STUDY_PATCH_08_OPEN_to_CLOSED() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("status", "CLOSED"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryStudy(studyId).getStatus().name()).isEqualTo("CLOSED");
    }

    // T-STUDY-PATCH-09: CLOSED → OPEN (정원 여유 있음)
    @Test
    void T_STUDY_PATCH_09_CLOSED_to_OPEN_여유() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createClosedStudy(leaderId, 5, 2, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("status", "OPEN"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryStudy(studyId).getStatus().name()).isEqualTo("OPEN");
    }

    // T-STUDY-PATCH-10: CLOSED → OPEN (정원 도달 → 차단)
    @Test
    void T_STUDY_PATCH_10_재오픈_정원초과_차단() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createClosedStudy(leaderId, 5, 5, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("status", "OPEN"));
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(queryStudy(studyId).getStatus().name()).isEqualTo("CLOSED");
    }

    // ── CLOSED 제한 분기 (D-015) ─────────────────────────────────────

    // T-STUDY-PATCH-13: CLOSED + maxMembers 수정 시도
    @Test
    void T_STUDY_PATCH_13_CLOSED_maxMembers_금지() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createClosedStudy(leaderId, 5, 2, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("maxMembers", 10));
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVALID_STATUS_FOR_UPDATE");
    }

    // T-STUDY-PATCH-14: CLOSED + durationWeeks 수정 시도
    @Test
    void T_STUDY_PATCH_14_CLOSED_durationWeeks_금지() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createClosedStudy(leaderId, 5, 2, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("durationWeeks", 8));
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVALID_STATUS_FOR_UPDATE");
    }

    // T-STUDY-PATCH-16: CLOSED + title/description 수정 가능
    @Test
    void T_STUDY_PATCH_16_CLOSED_title_허용() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createClosedStudy(leaderId, 5, 2, studyRepository, studyMemberRepository, jdbcTemplate);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("title", "마감 후 제목 변경"));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryStudy(studyId).getTitle()).isEqualTo("마감 후 제목 변경");
    }

    // ── maxMembers 분기 (D-011) ──────────────────────────────────────

    // T-STUDY-PATCH-18: maxMembers < currentMembers → 409
    @Test
    void T_STUDY_PATCH_18_maxMembers_현재보다_작게_금지() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);
        jdbcTemplate.update("UPDATE study SET current_member_count=3 WHERE id=?", studyId);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("maxMembers", 2));
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVALID_MAX_MEMBERS");
    }

    // T-STUDY-PATCH-19: maxMembers == currentMembers → 허용
    @Test
    void T_STUDY_PATCH_19_maxMembers_동일_허용() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createSimpleStudy(leaderId, 5, studyRepository, studyMemberRepository);
        jdbcTemplate.update("UPDATE study SET current_member_count=3 WHERE id=?", studyId);

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("maxMembers", 3));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    // ── tags / languages 교체 ─────────────────────────────────────────

    // T-STUDY-PATCH-22: tags 전체 교체
    @Test
    void T_STUDY_PATCH_22_tags_교체() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        var java   = TagFixture.persist("Java", tagRepository);
        var spring = TagFixture.persist("Spring", tagRepository);

        long studyId = StudyFixture.createOpenStudy(
                leaderId, 5,
                List.of(java.getId(), spring.getId()),
                List.of("ko"),
                studyRepository, studyMemberRepository, studyTagRepository, studyLanguageRepository
        );

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("tags", List.of("Go", "Rust", "C++")));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(studyTagRepository.findAllByIdStudyId(studyId)).hasSize(3);
    }

    // T-STUDY-PATCH-24: languages 전체 교체
    @Test
    void T_STUDY_PATCH_24_languages_교체() throws Exception {
        long leaderId = saveUser(TEST_EMAIL, TEST_NAME);
        long studyId = StudyFixture.createOpenStudy(
                leaderId, 5, List.of(), List.of("ko", "en"),
                studyRepository, studyMemberRepository, studyTagRepository, studyLanguageRepository
        );

        var res = patchStudy(bearerToken(leaderId), studyId, Map.of("languages", List.of("ja")));
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(studyLanguageRepository.findAllByIdStudyId(studyId)).hasSize(1);
    }
}
