package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostPatchIntegrationTest extends PostIntegrationTestBase {

    @Test
    void T_POST_PATCH_01_제목만_변경() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "old", "body");

        var res = patchPost(bearerToken(leaderId), studyId, postId, "{\"title\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        var post = queryPost(postId);
        assertThat(post.getTitle()).isEqualTo("new");
        assertThat(post.getContent()).isEqualTo("body");
    }

    @Test
    void T_POST_PATCH_02_content만_변경() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "old");

        var res = patchPost(bearerToken(leaderId), studyId, postId, "{\"content\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryPost(postId).getContent()).isEqualTo("new");
        assertThat(queryPost(postId).getTitle()).isEqualTo("t");
    }

    @Test
    void T_POST_PATCH_03_빈_body() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = patchPost(bearerToken(leaderId), studyId, postId, "{}");
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryPost(postId).getTitle()).isEqualTo("t");
        assertThat(queryPost(postId).getContent()).isEqualTo("c");
    }

    @Test
    void T_POST_PATCH_05_LEADER_타인글_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        long postId = savePost(studyId, memMemberId, PostType.FREE, "t", "c");

        var res = patchPost(bearerToken(leaderId), studyId, postId, "{\"title\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorMessage(res)).isEqualTo("게시글 수정 권한이 없습니다.");
    }

    @Test
    void T_POST_PATCH_06_다른_멤버_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long member2Id = saveUser(MEMBER2_EMAIL, "멤버2");
        long studyId = createOpenStudy(leaderId, 5);
        long memMemberId = addMember(studyId, memberId);
        addMember(studyId, member2Id);
        long postId = savePost(studyId, memMemberId, PostType.FREE, "t", "c");

        var res = patchPost(bearerToken(member2Id), studyId, postId, "{\"title\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void T_POST_PATCH_07_비멤버() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        var res = patchPost(bearerToken(outsiderId), studyId, postId, "{\"title\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void T_POST_PATCH_08_softdeleted_post() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");
        markPostDeleted(postId);

        var res = patchPost(bearerToken(leaderId), studyId, postId, "{\"title\":\"new\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_PATCH_11_title_초과() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long postId = savePost(studyId, leaderMemberId(studyId), PostType.FREE, "t", "c");

        String longTitle = "a".repeat(301);
        var res = patchPost(bearerToken(leaderId), studyId, postId,
                "{\"title\":\"" + longTitle + "\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }
}
