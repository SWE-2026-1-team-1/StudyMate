package com.studymate.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String school;

    @Column(length = 100)
    private String major;

    @Column(length = 255)
    private String bio;

    @Column(length = 500)
    private String interestTags;

    @Column(nullable = false)
    private boolean isEmailVerified = false;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {}

    // - 정적 팩토리: 가입 완료 시점 호출 (D-005 — 가입 시 isEmailVerified=true)
    public static User create(String email, String passwordHash, String name) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.isEmailVerified = true;
        user.isDeleted = false;
        Instant now = Instant.now();
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public void updateProfile(String name, String school, String major, String bio, String interestTags, Instant now) {
        this.name = name;
        this.school = school;
        this.major = major;
        this.bio = bio;
        this.interestTags = interestTags;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.isDeleted = true;
        this.updatedAt = now;
    }

    public Long getId()            { return id; }
    public String getEmail()       { return email; }
    public String getPasswordHash(){ return passwordHash; }
    public String getName()        { return name; }
    public String getSchool()      { return school; }
    public String getMajor()       { return major; }
    public String getBio()         { return bio; }
    public String getInterestTags(){ return interestTags; }
    public boolean isEmailVerified(){ return isEmailVerified; }
    public boolean isDeleted()     { return isDeleted; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }
}
