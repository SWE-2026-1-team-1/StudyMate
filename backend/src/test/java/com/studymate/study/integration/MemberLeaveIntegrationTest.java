package com.studymate.study.integration;

import com.studymate.study.domain.StudyMember;
import com.studymate.study.domain.StudyStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberLeaveIntegrationTest extends MemberIntegrationTestBase {

    // T-MEM-LEAVE-01
    @Test
    void T_MEM_LEAVE_01_정상_탈퇴() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memId = addMember(studyId, memberId);
        jdbcTemplate.update("UPDATE study SET current_member_count=2 WHERE id=?", studyId);

        var res = deleteSelf(bearerToken(memberId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);

        StudyMember self = queryMember(memId);
        assertThat(self.isActive()).isFalse();
        assertThat(self.getLeftReason()).isEqualTo("VOLUNTARY");
        assertThat(self.getLeftAt()).isNotNull();
        assertThat(queryStudy(studyId).getCurrentMemberCount()).isEqualTo(1);
        assertThat(queryStudy(studyId).getStatus()).isEqualTo(StudyStatus.OPEN);
    }

    // T-MEM-LEAVE-02 (CLOSED 유지)
    @Test
    void T_MEM_LEAVE_02_CLOSED_유지() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 2);
        addMember(studyId, memberId);
        markStudyClosed(studyId, 2);

        var res = deleteSelf(bearerToken(memberId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        assertThat(queryStudy(studyId).getStatus()).isEqualTo(StudyStatus.CLOSED);
        assertThat(queryStudy(studyId).getCurrentMemberCount()).isEqualTo(1);
    }

    // T-MEM-LEAVE-03 (LEADER 본인 탈퇴 시도)
    @Test
    void T_MEM_LEAVE_03_LEADER_탈퇴_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = deleteSelf(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("CONFLICT");
        assertThat(errorMessage(res)).isEqualTo("팀장은 탈퇴할 수 없습니다.");
        assertThat(queryMember(leaderMemberId(studyId)).isActive()).isTrue();
    }

    // T-MEM-LEAVE-04 (활성 멤버 아님)
    @Test
    void T_MEM_LEAVE_04_비_활성_멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);

        var res = deleteSelf(bearerToken(outsiderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-LEAVE-05 (미존재 teamId)
    @Test
    void T_MEM_LEAVE_05_미존재_teamId() throws Exception {
        long uid = saveUser(LEADER_EMAIL, "u");
        var res = deleteSelf(bearerToken(uid), 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-LEAVE-06 (soft-deleted study)
    @Test
    void T_MEM_LEAVE_06_soft_deleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);
        markStudyDeleted(studyId);

        var res = deleteSelf(bearerToken(memberId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-LEAVE-07 (미인증)
    @Test
    void T_MEM_LEAVE_07_미인증() throws Exception {
        var res = deleteSelfUnauth(1L);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
