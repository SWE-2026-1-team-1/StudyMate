package com.studymate.study.repository;

import com.studymate.study.domain.Study;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudyRepository extends JpaRepository<Study, Long> {

    Optional<Study> findByIdAndIsDeletedFalse(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Study s where s.id = :id and s.isDeleted = false")
    Optional<Study> findByIdAndNotDeletedForUpdate(@Param("id") long id);

    @Query("""
        select s from Study s
        where s.isDeleted = false
          and s.status = com.studymate.study.domain.StudyStatus.OPEN
        order by s.createdAt desc
    """)
    Page<Study> findAllOpenNotDeleted(Pageable pageable);
}
