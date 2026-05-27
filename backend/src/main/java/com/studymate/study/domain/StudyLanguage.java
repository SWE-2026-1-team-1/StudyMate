package com.studymate.study.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_language")
public class StudyLanguage {

    @EmbeddedId
    private StudyLanguageId id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected StudyLanguage() {}

    public static StudyLanguage of(Long studyId, String languageCode) {
        StudyLanguage sl = new StudyLanguage();
        sl.id = new StudyLanguageId(studyId, languageCode);
        sl.createdAt = LocalDateTime.now();
        return sl;
    }

    public StudyLanguageId getId()  { return id; }
    public Long getStudyId()        { return id.getStudyId(); }
    public String getLanguageCode() { return id.getLanguageCode(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
