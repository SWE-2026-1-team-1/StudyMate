package com.studymate.study.repository;

import com.studymate.study.domain.StudyTag;
import com.studymate.study.domain.StudyTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StudyTagRepository extends JpaRepository<StudyTag, StudyTagId> {

    List<StudyTag> findAllByIdStudyId(long studyId);

    @Query("select st from StudyTag st where st.id.studyId in :studyIds")
    List<StudyTag> findAllByStudyIdIn(@Param("studyIds") Collection<Long> studyIds);

    void deleteAllByIdStudyId(long studyId);
}
