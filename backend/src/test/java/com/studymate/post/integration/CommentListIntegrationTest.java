package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommentListIntegrationTest extends PostIntegrationTestBase {

    @Test
    @SuppressWarnings("unchecked")
    void T_CMT_LIST_01_오래된순_조회() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long c1 = saveComment(studyId, postId, lm, "first");
        Thread.sleep(10);
        long c2 = saveComment(studyId, postId, lm, "second");
        Thread.sleep(10);
        long c3 = saveComment(studyId, postId, lm, "third");

        var res = getComments(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> comments = (List<Map<String, Object>>) body.get("comments");
        assertThat(comments).hasSize(3);
        assertThat(body.get("totalCount")).isEqualTo(3);
        assertThat(comments.get(0).get("commentId")).isEqualTo(((Number) c1).intValue());
        assertThat(comments.get(2).get("commentId")).isEqualTo(((Number) c3).intValue());
        assertThat(comments.get(0).get("authorName")).isEqualTo("리더");
    }

    @Test
    @SuppressWarnings("unchecked")
    void T_CMT_LIST_02_삭제된_댓글_제외() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long c1 = saveComment(studyId, postId, lm, "x");
        long c2 = saveComment(studyId, postId, lm, "y");
        markCommentDeleted(c1);

        var res = getComments(bearerToken(leaderId), studyId, postId);
        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> comments = (List<Map<String, Object>>) body.get("comments");
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).get("commentId")).isEqualTo(((Number) c2).intValue());
    }

    @Test
    void T_CMT_LIST_03_post_softdeleted() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        markPostDeleted(postId);

        var res = getComments(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_CMT_LIST_04_비멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = getComments(bearerToken(outsiderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void T_CMT_LIST_05_미존재_post() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = getComments(bearerToken(leaderId), studyId, 99999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }
}
