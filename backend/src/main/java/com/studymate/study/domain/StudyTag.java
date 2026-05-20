package com.studymate.study.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_tag")
public class StudyTag {

    @EmbeddedId
    private StudyTagId id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected StudyTag() {}

    public static StudyTag of(Long studyId, Long tagId) {
        StudyTag st = new StudyTag();
        st.id = new StudyTagId(studyId, tagId);
        st.createdAt = LocalDateTime.now();
        return st;
    }

    public StudyTagId getId()   { return id; }
    public Long getStudyId()    { return id.getStudyId(); }
    public Long getTagId()      { return id.getTagId(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
