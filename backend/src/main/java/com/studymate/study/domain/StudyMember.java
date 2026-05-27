package com.studymate.study.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_member")
public class StudyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studyId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String roleCode;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    @Column(length = 20)
    private String leftReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StudyMember() {}

    public static StudyMember createLeader(Long studyId, Long userId) {
        return create(studyId, userId, "LEADER");
    }

    public static StudyMember createMember(Long studyId, Long userId) {
        return create(studyId, userId, "MEMBER");
    }

    private static StudyMember create(Long studyId, Long userId, String roleCode) {
        StudyMember sm = new StudyMember();
        sm.studyId = studyId;
        sm.userId = userId;
        sm.roleCode = roleCode;
        sm.isActive = true;
        LocalDateTime now = LocalDateTime.now();
        sm.joinedAt = now;
        sm.createdAt = now;
        sm.updatedAt = now;
        return sm;
    }

    public void kick() {
        if (!this.isActive) {
            throw new IllegalStateException("already inactive member");
        }
        LocalDateTime now = LocalDateTime.now();
        this.isActive   = false;
        this.leftReason = MemberLeftReason.KICKED.name();
        this.leftAt     = now;
        this.updatedAt  = now;
    }

    public void leave() {
        if (!this.isActive) {
            throw new IllegalStateException("already inactive member");
        }
        LocalDateTime now = LocalDateTime.now();
        this.isActive   = false;
        this.leftReason = MemberLeftReason.VOLUNTARY.name();
        this.leftAt     = now;
        this.updatedAt  = now;
    }

    public boolean isLeader() {
        return "LEADER".equals(this.roleCode);
    }

    public Long getId()             { return id; }
    public Long getStudyId()        { return studyId; }
    public Long getUserId()         { return userId; }
    public String getRoleCode()     { return roleCode; }
    public boolean isActive()       { return isActive; }
    public LocalDateTime getJoinedAt(){ return joinedAt; }
    public LocalDateTime getLeftAt(){ return leftAt; }
    public String getLeftReason()   { return leftReason; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }
    public String getName()         { return null; }
}
