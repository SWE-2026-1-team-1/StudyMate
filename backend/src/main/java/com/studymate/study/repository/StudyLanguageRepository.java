package com.studymate.study.repository;

import com.studymate.study.domain.StudyLanguage;
import com.studymate.study.domain.StudyLanguageId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyLanguageRepository extends JpaRepository<StudyLanguage, StudyLanguageId> {

    List<StudyLanguage> findAllByIdStudyId(long studyId);

    void deleteAllByIdStudyId(long studyId);
}
