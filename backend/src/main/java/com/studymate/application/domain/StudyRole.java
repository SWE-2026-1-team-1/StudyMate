package com.studymate.application.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_role")
public class StudyRole {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean canApproveApplication;

    @Column(nullable = false)
    private boolean canManageMember;

    @Column(nullable = false)
    private boolean canCreateAttendance;

    @Column(nullable = false)
    private boolean canPostNotice;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StudyRole() {}

    public String getCode()                   { return code; }
    public String getName()                   { return name; }
    public int getSortOrder()                 { return sortOrder; }
    public boolean isCanApproveApplication()  { return canApproveApplication; }
    public boolean isCanManageMember()        { return canManageMember; }
    public boolean isCanCreateAttendance()    { return canCreateAttendance; }
    public boolean isCanPostNotice()          { return canPostNotice; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public LocalDateTime getUpdatedAt()       { return updatedAt; }
}
