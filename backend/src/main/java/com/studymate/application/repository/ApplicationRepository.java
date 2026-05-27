package com.studymate.application.repository;

import com.studymate.application.domain.Application;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @Query("""
        select a from Application a
        where a.studyId = :studyId
          and a.applicantId = :applicantId
          and a.status = com.studymate.application.domain.ApplicationStatus.PENDING
    """)
    Optional<Application> findPending(@Param("studyId") long studyId,
                                      @Param("applicantId") long applicantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from Application a
        where a.studyId = :studyId
          and a.applicantId = :applicantId
          and a.status = com.studymate.application.domain.ApplicationStatus.PENDING
    """)
    Optional<Application> findPendingForUpdate(@Param("studyId") long studyId,
                                               @Param("applicantId") long applicantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from Application a
        where a.id = :id and a.studyId = :studyId
    """)
    Optional<Application> findByIdAndStudyIdForUpdate(@Param("id") long id,
                                                     @Param("studyId") long studyId);

    @Query("""
        select a from Application a
        where a.studyId = :studyId
          and a.status = com.studymate.application.domain.ApplicationStatus.PENDING
        order by a.appliedAt asc
    """)
    Page<Application> findAllPendingByStudyId(@Param("studyId") long studyId, Pageable pageable);

    Page<Application> findAllByApplicantIdOrderByAppliedAtDesc(long applicantId, Pageable pageable);
}
