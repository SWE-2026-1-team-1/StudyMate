package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommentCreateIntegrationTest extends PostIntegrationTestBase {

    @Test
    void T_CMT_CREATE_01_MEMBER_생성() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = createComment(bearerToken(memberId), studyId, postId, "{\"content\":\"hi\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> body = responseBody(res);
        assertThat(body.get("content")).isEqualTo("hi");
        assertThat(body.get("authorName")).isEqualTo("멤버");
        assertThat(postCommentRepository.findAll()).hasSize(1);
    }

    @Test
    void T_CMT_CREATE_02_LEADER_NOTICE_댓글() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.NOTICE, "공지", "내용");

        var res = createComment(bearerToken(leaderId), studyId, postId, "{\"content\":\"reply\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void T_CMT_CREATE_03_비멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = createComment(bearerToken(outsiderId), studyId, postId, "{\"content\":\"x\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void T_CMT_CREATE_04_post_softdeleted() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        markPostDeleted(postId);

        var res = createComment(bearerToken(leaderId), studyId, postId, "{\"content\":\"x\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_CMT_CREATE_07_content_blank() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = createComment(bearerToken(leaderId), studyId, postId, "{\"content\":\"\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void T_CMT_CREATE_08_content_초과() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        String big = "a".repeat(1001);

        var res = createComment(bearerToken(leaderId), studyId, postId, "{\"content\":\"" + big + "\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }
}
