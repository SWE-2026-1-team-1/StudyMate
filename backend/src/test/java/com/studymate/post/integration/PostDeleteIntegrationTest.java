package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostDeleteIntegrationTest extends PostIntegrationTestBase {

    @Test
    void T_POST_DELETE_01_작성자_삭제() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = deletePost(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        var post = queryPost(postId);
        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void T_POST_DELETE_02_LEADER_타인글_삭제() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        long postId = savePost(studyId, memMemberId, PostType.FREE, "t", "c");

        var res = deletePost(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        assertThat(queryPost(postId).isDeleted()).isTrue();
    }

    @Test
    void T_POST_DELETE_03_다른_멤버_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long member2Id = saveUser(MEMBER2_EMAIL, "멤버2");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        addMember(studyId, member2Id);
        long postId = savePost(studyId, memMemberId, PostType.FREE, "t", "c");

        var res = deletePost(bearerToken(member2Id), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorMessage(res)).isEqualTo("게시글 삭제 권한이 없습니다.");
        assertThat(queryPost(postId).isDeleted()).isFalse();
    }

    @Test
    void T_POST_DELETE_04_비멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = deletePost(bearerToken(outsiderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorMessage(res)).isEqualTo("팀 접근 권한이 없습니다.");
    }

    @Test
    void T_POST_DELETE_05_댓글있는_게시글_삭제() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long postId = savePost(studyId, lm, PostType.FREE, "t", "c");
        long c1 = saveComment(studyId, postId, lm, "c1");
        long c2 = saveComment(studyId, postId, lm, "c2");

        var res = deletePost(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(204);
        assertThat(queryPost(postId).isDeleted()).isTrue();
        assertThat(queryComment(c1).isDeleted()).isFalse();
        assertThat(queryComment(c2).isDeleted()).isFalse();
    }

    @Test
    void T_POST_DELETE_06_이미_삭제됨() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        markPostDeleted(postId);

        var res = deletePost(bearerToken(leaderId), studyId, postId);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_DELETE_07_미존재() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = deletePost(bearerToken(leaderId), studyId, 99999L);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_DELETE_08_인증없음() throws Exception {
        var res = deletePostUnauth(1L, 1L);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
