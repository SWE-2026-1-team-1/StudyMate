package com.studymate.study.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category = "GENERAL";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Tag() {}

    public static Tag create(String name) {
        Tag t = new Tag();
        t.name = name;
        t.category = "GENERAL";
        LocalDateTime now = LocalDateTime.now();
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    public Long getId()   { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
}
