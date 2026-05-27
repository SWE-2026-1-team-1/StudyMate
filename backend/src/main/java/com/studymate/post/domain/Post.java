package com.studymate.post.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long studyId;

    @Column(nullable = false)
    private long authorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostType type;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean isDeleted;

    private LocalDateTime deletedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Post() {}

    public static Post create(long studyId, long authorMemberId, PostType type, String title, String content) {
        Post p = new Post();
        p.studyId        = studyId;
        p.authorMemberId = authorMemberId;
        p.type           = type;
        p.title          = title;
        p.content        = content;
        p.isDeleted      = false;
        LocalDateTime now = LocalDateTime.now();
        p.createdAt      = now;
        p.updatedAt      = now;
        return p;
    }

    public void update(String title, String content) {
        if (title != null)   this.title   = title;
        if (content != null) this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        if (this.isDeleted) {
            throw new IllegalStateException("already deleted post");
        }
        LocalDateTime now = LocalDateTime.now();
        this.isDeleted = true;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Long getId()                 { return id; }
    public long getStudyId()            { return studyId; }
    public long getAuthorMemberId()     { return authorMemberId; }
    public PostType getType()           { return type; }
    public String getTitle()            { return title; }
    public String getContent()          { return content; }
    public boolean isDeleted()          { return isDeleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
