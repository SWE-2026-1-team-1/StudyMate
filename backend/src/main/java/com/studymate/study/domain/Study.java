package com.studymate.study.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study")
public class Study {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(nullable = false)
    private int maxMembers;

    @Column(nullable = false)
    private int currentMemberCount = 0;

    @Column(nullable = false, length = 100)
    private String activityCycle;

    @Column(nullable = false)
    private int durationWeeks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyStatus status = StudyStatus.OPEN;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Study() {}

    public static Study create(String title, String purpose, int maxMembers,
                               int durationWeeks, String activityCycle) {
        Study s = new Study();
        s.title = title;
        s.purpose = purpose;
        s.maxMembers = maxMembers;
        s.currentMemberCount = 1;
        s.durationWeeks = durationWeeks;
        s.activityCycle = activityCycle;
        s.status = StudyStatus.OPEN;
        s.isDeleted = false;
        LocalDateTime now = LocalDateTime.now();
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    // 도메인 메서드
    public void rename(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public void changePurpose(String purpose) {
        this.purpose = purpose;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeActivityCycle(String cycle) {
        this.activityCycle = cycle;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeDurationWeeks(int weeks) {
        this.durationWeeks = weeks;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeMaxMembers(int max) {
        this.maxMembers = max;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeStatus(StudyStatus next) {
        this.status = next;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDeleted() {
        this.isDeleted = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementMemberCount() {
        this.currentMemberCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decrementMemberCount() {
        this.currentMemberCount--;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId()              { return id; }
    public String getTitle()         { return title; }
    public String getPurpose()       { return purpose; }
    public int getMaxMembers()       { return maxMembers; }
    public int getCurrentMemberCount(){ return currentMemberCount; }
    public String getActivityCycle() { return activityCycle; }
    public int getDurationWeeks()    { return durationWeeks; }
    public StudyStatus getStatus()   { return status; }
    public boolean isDeleted()       { return isDeleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
