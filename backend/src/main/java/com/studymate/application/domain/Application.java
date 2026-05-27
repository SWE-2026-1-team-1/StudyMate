package com.studymate.application.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studyId;

    @Column(nullable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(length = 500)
    private String message;

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime processedAt;

    private Long processedByMemberId;

    @Column(length = 500)
    private String rejectReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Application() {}

    public static Application createPending(Long studyId, Long applicantId, String message) {
        Application a = new Application();
        a.studyId = studyId;
        a.applicantId = applicantId;
        a.status = ApplicationStatus.PENDING;
        a.message = message;
        LocalDateTime now = LocalDateTime.now();
        a.appliedAt = now;
        a.createdAt = now;
        a.updatedAt = now;
        return a;
    }

    public void accept(long processedByMemberId) {
        this.status = ApplicationStatus.ACCEPTED;
        this.processedByMemberId = processedByMemberId;
        LocalDateTime now = LocalDateTime.now();
        this.processedAt = now;
        this.updatedAt = now;
    }

    public void reject(long processedByMemberId, String reason) {
        this.status = ApplicationStatus.REJECTED;
        this.processedByMemberId = processedByMemberId;
        this.rejectReason = reason;
        LocalDateTime now = LocalDateTime.now();
        this.processedAt = now;
        this.updatedAt = now;
    }

    public boolean isPending() { return status == ApplicationStatus.PENDING; }

    public Long getId()                  { return id; }
    public Long getStudyId()             { return studyId; }
    public Long getApplicantId()         { return applicantId; }
    public ApplicationStatus getStatus() { return status; }
    public String getMessage()           { return message; }
    public LocalDateTime getAppliedAt()  { return appliedAt; }
    public LocalDateTime getProcessedAt(){ return processedAt; }
    public Long getProcessedByMemberId() { return processedByMemberId; }
    public String getRejectReason()      { return rejectReason; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }
}
