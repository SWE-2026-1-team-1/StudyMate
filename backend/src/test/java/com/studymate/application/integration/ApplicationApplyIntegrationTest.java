package com.studymate.application.integration;

import com.studymate.application.fixture.ApplicationFixture;
import com.studymate.application.fixture.StudyMemberFixture;
import com.studymate.study.domain.Study;
import com.studymate.study.domain.StudyStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationApplyIntegrationTest extends ApplicationIntegrationTestBase {

    private long createOpenStudy(long leaderId, int maxMembers) {
        Study study = studyRepository.save(Study.create("스터디", "설명", maxMembers, 4, "주1회"));
        StudyMemberFixture.leader(study.getId(), leaderId, studyMemberRepository);
        return study.getId();
    }

    // T-APP-CREATE-01
    @Test
    void T_APP_CREATE_01_정상_지원() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);

        var res = postApply(bearerToken(applicantId), studyId, Map.of("message", "하고싶어요"));
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> resp = responseBody(res);
        assertThat(resp.get("status")).isEqualTo("PENDING");
        long appId = ((Number) resp.get("applicationId")).longValue();

        assertThat(queryApplicationStatus(appId)).isEqualTo("PENDING");
    }

    // T-APP-CREATE-02
    @Test
    void T_APP_CREATE_02_빈_본문_message_null() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);

        var res = postApply(bearerToken(applicantId), studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
    }

    // T-APP-CREATE-04
    @Test
    void T_APP_CREATE_04_미존재_studyId() throws Exception {
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        var res = postApply(bearerToken(applicantId), 9999L, Map.of("message", "x"));
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("NOT_FOUND");
    }

    // T-APP-CREATE-05
    @Test
    void T_APP_CREATE_05_soft_deleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);
        jdbcTemplate.update("UPDATE study SET is_deleted=1 WHERE id=?", studyId);

        var res = postApply(bearerToken(applicantId), studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-APP-CREATE-06
    @Test
    void T_APP_CREATE_06_CLOSED_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);
        jdbcTemplate.update("UPDATE study SET status='CLOSED' WHERE id=?", studyId);

        var res = postApply(bearerToken(applicantId), studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("STUDY_FULL");
        assertThat(applicationRepository.count()).isZero();
    }

    // T-APP-CREATE-07
    @Test
    void T_APP_CREATE_07_이미_활성_멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);
        StudyMemberFixture.member(studyId, applicantId, studyMemberRepository);

        var res = postApply(bearerToken(applicantId), studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("ALREADY_MEMBER");
    }

    // T-APP-CREATE-08
    @Test
    void T_APP_CREATE_08_이미_PENDING() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);
        ApplicationFixture.pending(studyId, applicantId, "first", applicationRepository);

        var res = postApply(bearerToken(applicantId), studyId, Map.of("message", "second"));
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("ALREADY_APPLIED");
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    // T-APP-CREATE-09
    @Test
    void T_APP_CREATE_09_REJECTED_재신청_허용() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);
        long leaderMemberId = studyMemberRepository.findActiveLeader(studyId).orElseThrow().getId();
        ApplicationFixture.rejected(studyId, applicantId, leaderMemberId,
                applicationRepository, jdbcTemplate);

        var res = postApply(bearerToken(applicantId), studyId, Map.of("message", "다시"));
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        assertThat(applicationRepository.count()).isEqualTo(2);
    }

    // T-APP-CREATE-10
    @Test
    void T_APP_CREATE_10_과거_ACCEPTED_탈퇴_후_재지원() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);

        // 비활성 멤버 row (탈퇴 상태) — JdbcTemplate 직접 INSERT
        jdbcTemplate.update("""
            INSERT INTO study_member
              (study_id, user_id, role_code, is_active, joined_at, left_at, left_reason,
               created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
        """, studyId, applicantId, "MEMBER", false,
                java.sql.Timestamp.from(java.time.Instant.now()),
                java.sql.Timestamp.from(java.time.Instant.now()),
                "VOLUNTARY",
                java.sql.Timestamp.from(java.time.Instant.now()),
                java.sql.Timestamp.from(java.time.Instant.now()));

        var res = postApply(bearerToken(applicantId), studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
    }

    // T-APP-CREATE-11
    @Test
    void T_APP_CREATE_11_LEADER_본인_지원() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = postApply(bearerToken(leaderId), studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("ALREADY_MEMBER");
    }

    // T-APP-CREATE-12
    @Test
    void T_APP_CREATE_12_인증_없음() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = postApply("", studyId, Map.of());
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // T-APP-CREATE-13: message 길이 위반
    @Test
    void T_APP_CREATE_13_message_501자() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long applicantId = saveUser(APPLICANT_EMAIL, "지원자");
        long studyId = createOpenStudy(leaderId, 5);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "a".repeat(501));

        var res = postApply(bearerToken(applicantId), studyId, body);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_INPUT");
    }
}
