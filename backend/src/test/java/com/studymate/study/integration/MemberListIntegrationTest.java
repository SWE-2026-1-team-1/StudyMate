package com.studymate.study.integration;

import com.studymate.study.domain.StudyMember;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemberListIntegrationTest extends MemberIntegrationTestBase {

    // T-MEM-LIST-01
    @Test
    @SuppressWarnings("unchecked")
    void T_MEM_LIST_01_가입순_정렬() throws Exception {
        long leaderId  = saveUser(LEADER_EMAIL,  "리더");
        long memberId  = saveUser(MEMBER_EMAIL,  "멤버1");
        long member2Id = saveUser(MEMBER2_EMAIL, "멤버2");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);
        addMember(studyId, member2Id);

        var res = getMembers(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
        assertThat(members).hasSize(3);
        assertThat(body.get("totalCount")).isEqualTo(3);
        assertThat(members.get(0).get("roleCode")).isEqualTo("LEADER");
        assertThat(members.get(0).get("userId")).isEqualTo(((Number) leaderId).intValue());
        assertThat(members.get(1).get("userId")).isEqualTo(((Number) memberId).intValue());
        assertThat(members.get(2).get("userId")).isEqualTo(((Number) member2Id).intValue());
        assertThat(members.get(0).get("userName")).isEqualTo("리더");
    }

    // T-MEM-LIST-02
    @Test
    @SuppressWarnings("unchecked")
    void T_MEM_LIST_02_강퇴된_멤버_제외() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memId = addMember(studyId, memberId);
        // 강퇴 처리
        StudyMember target = queryMember(memId);
        target.kick();
        studyMemberRepository.save(target);

        var res = getMembers(bearerToken(leaderId), studyId);
        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
        assertThat(members).hasSize(1);
    }

    // T-MEM-LIST-03
    @Test
    @SuppressWarnings("unchecked")
    void T_MEM_LIST_03_LEADER_혼자() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = getMembers(bearerToken(leaderId), studyId);
        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("roleCode")).isEqualTo("LEADER");
    }

    // T-MEM-LIST-04
    @Test
    void T_MEM_LIST_04_비_멤버_차단() throws Exception {
        long leaderId   = saveUser(LEADER_EMAIL,   "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);

        var res = getMembers(bearerToken(outsiderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("FORBIDDEN");
    }

    // T-MEM-LIST-05
    @Test
    void T_MEM_LIST_05_탈퇴한_멤버_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memId = addMember(studyId, memberId);
        StudyMember t = queryMember(memId); t.leave(); studyMemberRepository.save(t);

        var res = getMembers(bearerToken(memberId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    // T-MEM-LIST-06
    @Test
    void T_MEM_LIST_06_MEMBER_조회_허용() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);

        var res = getMembers(bearerToken(memberId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    // T-MEM-LIST-07
    @Test
    void T_MEM_LIST_07_미존재_teamId() throws Exception {
        long uid = saveUser(LEADER_EMAIL, "u");
        var res = getMembers(bearerToken(uid), 9999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-LIST-08
    @Test
    void T_MEM_LIST_08_soft_deleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        markStudyDeleted(studyId);

        var res = getMembers(bearerToken(leaderId), studyId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // T-MEM-LIST-09
    @Test
    void T_MEM_LIST_09_미인증() throws Exception {
        var res = getMembersUnauth(1L);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
