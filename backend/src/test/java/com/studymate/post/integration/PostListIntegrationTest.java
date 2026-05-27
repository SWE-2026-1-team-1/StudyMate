package com.studymate.post.integration;

import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostListIntegrationTest extends PostIntegrationTestBase {

    @Test
    @SuppressWarnings("unchecked")
    void T_POST_LIST_01_최신순_조회() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long p1 = savePost(studyId, lm, PostType.FREE, "t1", "c1");
        Thread.sleep(10);
        long p2 = savePost(studyId, lm, PostType.NOTICE, "t2", "c2");
        Thread.sleep(10);
        long p3 = savePost(studyId, lm, PostType.FREE, "t3", "c3");

        var res = getPosts(bearerToken(leaderId), studyId, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> posts = (List<Map<String, Object>>) body.get("posts");
        assertThat(posts).hasSize(3);
        assertThat(body.get("totalCount")).isEqualTo(3);
        assertThat(posts.get(0).get("postId")).isEqualTo(((Number) p3).intValue());
        assertThat(posts.get(1).get("postId")).isEqualTo(((Number) p2).intValue());
        assertThat(posts.get(2).get("postId")).isEqualTo(((Number) p1).intValue());
        assertThat(posts.get(0).get("authorName")).isEqualTo("리더");
    }

    @Test
    @SuppressWarnings("unchecked")
    void T_POST_LIST_02_삭제글_제외() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        long p1 = savePost(studyId, lm, PostType.FREE, "t1", "c1");
        long p2 = savePost(studyId, lm, PostType.FREE, "t2", "c2");
        markPostDeleted(p1);

        var res = getPosts(bearerToken(leaderId), studyId, 0, 20);
        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> posts = (List<Map<String, Object>>) body.get("posts");
        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).get("postId")).isEqualTo(((Number) p2).intValue());
    }

    @Test
    void T_POST_LIST_04_비멤버_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);

        var res = getPosts(bearerToken(outsiderId), studyId, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("FORBIDDEN");
    }

    @Test
    void T_POST_LIST_06_멤버_조회_허용() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);

        var res = getPosts(bearerToken(memberId), studyId, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void T_POST_LIST_07_미존재_team() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        var res = getPosts(bearerToken(leaderId), 99999L, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_LIST_08_softdeleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        markStudyDeleted(studyId);

        var res = getPosts(bearerToken(leaderId), studyId, 0, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_LIST_09_인증없음() throws Exception {
        var res = getPostsUnauth(1L);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void T_POST_LIST_10_페이징_size_20() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        for (int i = 0; i < 25; i++) savePost(studyId, lm, PostType.FREE, "t" + i, "c");

        var res = getPosts(bearerToken(leaderId), studyId, 0, 20);
        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> posts = (List<Map<String, Object>>) body.get("posts");
        assertThat(posts).hasSize(20);
        assertThat(body.get("totalCount")).isEqualTo(25);
    }

    @Test
    @SuppressWarnings("unchecked")
    void T_POST_LIST_11_페이징_page1() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        long lm = leaderMemberId(studyId);
        for (int i = 0; i < 25; i++) savePost(studyId, lm, PostType.FREE, "t" + i, "c");

        var res = getPosts(bearerToken(leaderId), studyId, 1, 20);
        Map<String, Object> body = responseBody(res);
        List<Map<String, Object>> posts = (List<Map<String, Object>>) body.get("posts");
        assertThat(posts).hasSize(5);
    }

    @Test
    void T_POST_LIST_12_size_101() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        var res = getPosts(bearerToken(leaderId), studyId, 0, 101);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void T_POST_LIST_13_page_음수() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        var res = getPosts(bearerToken(leaderId), studyId, -1, 20);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }
}
