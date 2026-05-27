package com.studymate.post.integration;

import com.studymate.post.domain.Post;
import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostCreateIntegrationTest extends PostIntegrationTestBase {

    @Test
    void T_POST_CREATE_01_FREE_생성() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"제목\",\"content\":\"본문\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> body = responseBody(res);
        assertThat(body.get("title")).isEqualTo("제목");

        List<Post> all = postRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getType()).isEqualTo(PostType.FREE);
        assertThat(all.get(0).isDeleted()).isFalse();
    }

    @Test
    void T_POST_CREATE_02_LEADER_NOTICE_생성() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);

        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"공지\",\"content\":\"내용\",\"type\":\"NOTICE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        assertThat(postRepository.findAll().get(0).getType()).isEqualTo(PostType.NOTICE);
    }

    @Test
    void T_POST_CREATE_03_MEMBER_NOTICE_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);

        var res = createPost(bearerToken(memberId), studyId,
                "{\"title\":\"공지\",\"content\":\"내용\",\"type\":\"NOTICE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("FORBIDDEN");
        assertThat(errorMessage(res)).isEqualTo("공지 작성 권한이 없습니다.");
        assertThat(postRepository.findAll()).isEmpty();
    }

    @Test
    void T_POST_CREATE_04_MEMBER_FREE_생성() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long memberId = saveUser(MEMBER_EMAIL, "멤버");
        long studyId = createOpenStudy(leaderId, 5);
        addMember(studyId, memberId);

        var res = createPost(bearerToken(memberId), studyId,
                "{\"title\":\"제목\",\"content\":\"본문\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void T_POST_CREATE_05_비멤버_차단() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long outsiderId = saveUser(OUTSIDER_EMAIL, "외부");
        long studyId = createOpenStudy(leaderId, 5);

        var res = createPost(bearerToken(outsiderId), studyId,
                "{\"title\":\"제목\",\"content\":\"본문\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorMessage(res)).isEqualTo("팀 접근 권한이 없습니다.");
    }

    @Test
    void T_POST_CREATE_06_미존재_team() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        var res = createPost(bearerToken(leaderId), 99999L,
                "{\"title\":\"제목\",\"content\":\"본문\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_CREATE_07_softdeleted_study() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        markStudyDeleted(studyId);
        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"제목\",\"content\":\"본문\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void T_POST_CREATE_09_title_blank() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"\",\"content\":\"본문\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_INPUT");
    }

    @Test
    void T_POST_CREATE_10_title_초과() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        String longTitle = "a".repeat(301);
        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"" + longTitle + "\",\"content\":\"본문\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void T_POST_CREATE_11_content_blank() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"제목\",\"content\":\"\",\"type\":\"FREE\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void T_POST_CREATE_13_type_누락() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"제목\",\"content\":\"본문\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void T_POST_CREATE_14_type_unknown() throws Exception {
        long leaderId = saveUser(LEADER_EMAIL, "리더");
        long studyId = createOpenStudy(leaderId, 5);
        var res = createPost(bearerToken(leaderId), studyId,
                "{\"title\":\"제목\",\"content\":\"본문\",\"type\":\"UNKNOWN\"}");
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }
}
