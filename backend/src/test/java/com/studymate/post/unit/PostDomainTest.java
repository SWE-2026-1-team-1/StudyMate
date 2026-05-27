package com.studymate.post.unit;

import com.studymate.post.domain.Post;
import com.studymate.post.domain.PostComment;
import com.studymate.post.domain.PostType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostDomainTest {

    @Test
    void create_초기상태() {
        Post p = Post.create(1L, 2L, PostType.FREE, "t", "c");
        assertThat(p.getStudyId()).isEqualTo(1L);
        assertThat(p.getAuthorMemberId()).isEqualTo(2L);
        assertThat(p.getType()).isEqualTo(PostType.FREE);
        assertThat(p.isDeleted()).isFalse();
        assertThat(p.getCreatedAt()).isEqualTo(p.getUpdatedAt());
    }

    @Test
    void update_부분필드() {
        Post p = Post.create(1L, 2L, PostType.FREE, "t", "c");
        p.update("new", null);
        assertThat(p.getTitle()).isEqualTo("new");
        assertThat(p.getContent()).isEqualTo("c");
    }

    @Test
    void softDelete_정상() {
        Post p = Post.create(1L, 2L, PostType.FREE, "t", "c");
        p.softDelete();
        assertThat(p.isDeleted()).isTrue();
        assertThat(p.getDeletedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isEqualTo(p.getDeletedAt());
    }

    @Test
    void softDelete_이미_삭제됨() {
        Post p = Post.create(1L, 2L, PostType.FREE, "t", "c");
        p.softDelete();
        assertThatThrownBy(p::softDelete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void comment_create_및_update() {
        PostComment c = PostComment.create(1L, 2L, 3L, "old");
        assertThat(c.getStudyId()).isEqualTo(1L);
        c.update("new");
        assertThat(c.getContent()).isEqualTo("new");
    }

    @Test
    void comment_softDelete_및_이중호출() {
        PostComment c = PostComment.create(1L, 2L, 3L, "x");
        c.softDelete();
        assertThat(c.isDeleted()).isTrue();
        assertThatThrownBy(c::softDelete).isInstanceOf(IllegalStateException.class);
    }
}
