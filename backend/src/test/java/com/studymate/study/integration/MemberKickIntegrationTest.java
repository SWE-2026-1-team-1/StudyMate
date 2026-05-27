package com.studymate.study.integration;

import com.studymate.study.domain.StudyMember;
import com.studymate.study.domain.StudyStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberKickIntegrationTest extends MemberIntegrationTestBase {

    // T-MEM-KICK-01
    @Test
    void T_MEM_KICK_01_정상_강퇴() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memId = addMember(studyId, memberId);
        jdbcTemplate.update("UPDATE study SET current_member_count=2 WHERE id=?", studyId);

        var res = deleteMember(bearerToken(leaderId), studyId, memId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);

        StudyMember target = queryMember(memId);
        assertThat(target.isActive()).isFalse();
        assertThat(target.getLeftReason()).isEqualTo("KICKED");
        assertThat(target.getLeftAt()).isNotNull();
        assertThat(queryStudy(studyId).getCurrentMemberCount()).isEqualTo(1);
        assertThat(queryStudy(studyId).getStatus()).isEqualTo(StudyStatus.OPEN);
    }

    // T-MEM-KICK-02 (CLOSED 유지)
    @Test
    void T_MEM_KICK_02_CLOSED_유지() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 2);
        long memId = addMember(studyId, memberId);
        markStudyClosed(studyId, 2);

        var res = deleteMember(bearerToken(leaderId), studyId, memId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        assertThat(queryStudy(studyId).getStatus()).isEqualTo(StudyStatus.CLOSED);
        assertThat(queryStudy(studyId).getCurrentMemberCount()).isEqualTo(1);
    }

    // T-MEM-KICK-03 (LEADER 본인 강퇴 시도)
    @Test
    void T_MEM_KICK_03_LEADER_자기_강퇴() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long leaderMemId = leaderMemberId(studyId);

        var res = deleteMember(bearerToken(leaderId), studyId, leaderMemId);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("CANNOT_REMOVE_LEADER");
        assertThat(queryMember(leaderMemId).isActive()).isTrue();
    }

    // T-MEM-KICK-05 (이미 inactive)
    @Test
    void T_MEM_KICK_05_이미_inactive() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memId = addMember(studyId, memberId);
        StudyMember t = queryMember(memId); t.kick(); studyMemberRepository.save(t);

        var res = deleteMember(bearerToken(leaderId), studyId, memId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-KICK-06 (다른 스터디의 memberId)
    @Test
    void T_MEM_KICK_06_studyId_불일치() throws Exception {
        long leaderId  = saveUser(LEADER_EMAIL, "리더");
        long memberId  = saveUser(MEMBER_EMAIL, "멤버");
        long otherLeaderId = saveUser(MEMBER2_EMAIL, "타리더");
        long studyId  = createOpenStudy(leaderId, 5);
        long otherStudyId = createOpenStudy(otherLeaderId, 5);
        long otherMemId = addMember(otherStudyId, memberId);

        var res = deleteMember(bearerToken(leaderId), studyId, otherMemId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-KICK-07 (비-LEADER)
    @Test
    void T_MEM_KICK_07_비_LEADER_차단() throws Exception {
        long leaderId  = saveUser(LEADER_EMAIL, "리더");
        long memberId  = saveUser(MEMBER_EMAIL, "멤버");
        long member2Id = saveUser(MEMBER2_EMAIL, "멤버2");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);
        long targetMemId = addMember(studyId, member2Id);

        var res = deleteMember(bearerToken(memberId), studyId, targetMemId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(queryMember(targetMemId).isActive()).isTrue();
    }

    // T-MEM-KICK-08 (비-멤버)
    @Test
    void T_MEM_KICK_08_비_멤버_차단() throws Exception {
        long leaderId   = saveUser(LEADER_EMAIL,   "리더");
        long memberId   = saveUser(MEMBER_EMAIL,   "멤버");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long memId = addMember(studyId, memberId);

        var res = deleteMember(bearerToken(outsiderId), studyId, memId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    // T-MEM-KICK-09 (미존재 memberId)
    @Test
    void T_MEM_KICK_09_미존재_memberId() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = deleteMember(bearerToken(leaderId), studyId, 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-KICK-10 (미존재 teamId)
    @Test
    void T_MEM_KICK_10_미존재_teamId() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        var res = deleteMember(bearerToken(leaderId), 9999L, 1L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-KICK-11 (soft-deleted study)
    @Test
    void T_MEM_KICK_11_soft_deleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memId = addMember(studyId, memberId);
        markStudyDeleted(studyId);

        var res = deleteMember(bearerToken(leaderId), studyId, memId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-KICK-12 (미인증)
    @Test
    void T_MEM_KICK_12_미인증() throws Exception {
        var res = deleteMemberUnauth(1L, 1L);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
