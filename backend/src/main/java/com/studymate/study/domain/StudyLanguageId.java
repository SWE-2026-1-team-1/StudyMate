package com.studymate.study.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StudyLanguageId implements Serializable {

    private Long studyId;
    private String languageCode;

    protected StudyLanguageId() {}

    public StudyLanguageId(Long studyId, String languageCode) {
        this.studyId = studyId;
        this.languageCode = languageCode;
    }

    public Long getStudyId()       { return studyId; }
    public String getLanguageCode(){ return languageCode; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudyLanguageId id)) return false;
        return Objects.equals(studyId, id.studyId) && Objects.equals(languageCode, id.languageCode);
    }

    @Override
    public int hashCode() { return Objects.hash(studyId, languageCode); }
}
