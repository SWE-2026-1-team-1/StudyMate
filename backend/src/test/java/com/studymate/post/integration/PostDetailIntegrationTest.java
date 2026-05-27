package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostDetailIntegrationTest extends PostIntegrationTestBase {

    @Test
    void T_POST_DETAIL_01_정상() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t1", "c1");

        var res = getPost(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = responseBody(res);
        assertThat(body.get("title")).isEqualTo("t1");
        assertThat(body.get("content")).isEqualTo("c1");
        assertThat(body.get("type")).isEqualTo("FREE");
        assertThat(body.get("authorName")).isEqualTo("리더");
    }

    @Test
    void T_POST_DETAIL_02_MEMBER_NOTICE_조회() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.NOTICE, "공지", "내용");

        var res = getPost(bearerToken(memberId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void T_POST_DETAIL_03_softdeleted_post() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        markPostDeleted(postId);

        var res = getPost(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_DETAIL_04_다른_study_post() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long leader2Id = saveUser(MEMBER_EMAIL, "리더2");
        long s1 = createOpenStudy(leaderId, 5);
        long s2 = createOpenStudy(leader2Id, 5);
        long postIn2 = savePost(s2, leaderMemberId(s2), PostType.FREE, "t", "c");

        var res = getPost(bearerToken(leaderId), s1, postIn2);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_DETAIL_05_비멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = getPost(bearerToken(outsiderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void T_POST_DETAIL_06_미존재_post() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = getPost(bearerToken(leaderId), studyId, 99999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_DETAIL_08_인증없음() throws Exception {
        var res = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/teams/1/posts/1")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
