package com.studymate.study.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudyTagId implements Serializable {

    private Long studyId;
    private Long tagId;

    protected StudyTagId() {}

    public StudyTagId(Long studyId, Long tagId) {
        this.studyId = studyId;
        this.tagId = tagId;
    }

    public Long getStudyId() { return studyId; }
    public Long getTagId()   { return tagId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudyTagId id)) return false;
        return Objects.equals(studyId, id.studyId) && Objects.equals(tagId, id.tagId);
    }

    @Override
    public int hashCode() { return Objects.hash(studyId, tagId); }
}
