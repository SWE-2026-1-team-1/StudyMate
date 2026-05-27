package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentDeleteIntegrationTest extends PostIntegrationTestBase {

    @Test
    void T_CMT_DELETE_01_작성자_삭제() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, memMemberId, "x");

        var res = deleteComment(bearerToken(memberId), studyId, postId, cmtId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        var c = queryComment(cmtId);
        assertThat(c.isDeleted()).isTrue();
        assertThat(c.getDeletedAt()).isNotNull();
    }

    @Test
    void T_CMT_DELETE_02_LEADER_타인_댓글_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, memMemberId, "x");

        var res = deleteComment(bearerToken(leaderId), studyId, postId, cmtId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorMessage(res)).isEqualTo("댓글 삭제 권한이 없습니다.");
        assertThat(queryComment(cmtId).isDeleted()).isFalse();
    }

    @Test
    void T_CMT_DELETE_04_비멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, lm, "x");

        var res = deleteComment(bearerToken(outsiderId), studyId, postId, cmtId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void T_CMT_DELETE_05_이미_삭제됨() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, lm, "x");
        markCommentDeleted(cmtId);

        var res = deleteComment(bearerToken(leaderId), studyId, postId, cmtId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_CMT_DELETE_06_post_softdeleted() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, lm, "x");
        markPostDeleted(postId);

        var res = deleteComment(bearerToken(leaderId), studyId, postId, cmtId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_CMT_DELETE_07_미존재_comment() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = deleteComment(bearerToken(leaderId), studyId, postId, 99999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }
}
