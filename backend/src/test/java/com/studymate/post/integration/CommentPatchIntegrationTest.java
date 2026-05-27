package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentPatchIntegrationTest extends PostIntegrationTestBase {

    @Test
    void T_CMT_PATCH_01_작성자_수정() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, memMemberId, "old");

        var res = patchComment(bearerToken(memberId), studyId, postId, cmtId, "{\"content\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryComment(cmtId).getContent()).isEqualTo("new");
    }

    @Test
    void T_CMT_PATCH_02_LEADER_타인_댓글_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, memMemberId, "x");

        var res = patchComment(bearerToken(leaderId), studyId, postId, cmtId, "{\"content\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorMessage(res)).isEqualTo("댓글 수정 권한이 없습니다.");
    }

    @Test
    void T_CMT_PATCH_04_삭제된_댓글() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, lm, "x");
        markCommentDeleted(cmtId);

        var res = patchComment(bearerToken(leaderId), studyId, postId, cmtId, "{\"content\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_CMT_PATCH_05_post_softdeleted() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, lm, "x");
        markPostDeleted(postId);

        var res = patchComment(bearerToken(leaderId), studyId, postId, cmtId, "{\"content\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_CMT_PATCH_07_비멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, lm, "x");

        var res = patchComment(bearerToken(outsiderId), studyId, postId, cmtId, "{\"content\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void T_CMT_PATCH_10_content_blank() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long cmtId = saveComment(studyId, postId, lm, "x");

        var res = patchComment(bearerToken(leaderId), studyId, postId, cmtId, "{\"content\":\"\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }
}
