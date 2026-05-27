package com.studymate.post.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_comment")
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long studyId;

    @Column(nullable = false)
    private long postId;

    @Column(nullable = false)
    private long authorMemberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean isDeleted;

    private LocalDateTime deletedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PostComment() {}

    public static PostComment create(long studyId, long postId, long authorMemberId, String content) {
        PostComment c = new PostComment();
        c.studyId        = studyId;
        c.postId         = postId;
        c.authorMemberId = authorMemberId;
        c.content        = content;
        c.isDeleted      = false;
        LocalDateTime now = LocalDateTime.now();
        c.createdAt      = now;
        c.updatedAt      = now;
        return c;
    }

    public void update(String content) {
        this.content   = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        if (this.isDeleted) {
            throw new IllegalStateException("already deleted comment");
        }
        LocalDateTime now = LocalDateTime.now();
        this.isDeleted = true;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Long getId()                 { return id; }
    public long getStudyId()            { return studyId; }
    public long getPostId()             { return postId; }
    public long getAuthorMemberId()     { return authorMemberId; }
    public String getContent()          { return content; }
    public boolean isDeleted()          { return isDeleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
