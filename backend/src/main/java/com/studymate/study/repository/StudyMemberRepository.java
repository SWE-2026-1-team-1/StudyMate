package com.studymate.study.repository;

import com.studymate.study.domain.StudyMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyMemberRepository extends JpaRepository<StudyMember, Long> {

    Optional<StudyMember> findByStudyIdAndUserIdAndIsActiveTrue(long studyId, long userId);

    @Query("""
        select sm from StudyMember sm
        where sm.studyId = :studyId
          and sm.roleCode = 'LEADER'
          and sm.isActive = true
    """)
    Optional<StudyMember> findActiveLeader(@Param("studyId") long studyId);

    boolean existsByStudyIdAndUserIdAndRoleCodeAndIsActiveTrue(long studyId, long userId, String roleCode);

    boolean existsByStudyIdAndUserIdAndIsActiveTrue(long studyId, long userId);

    @Query("""
        select sm from StudyMember sm, com.studymate.application.domain.StudyRole r
        where r.code = sm.roleCode
          and sm.studyId = :studyId
          and sm.userId  = :userId
          and sm.isActive = true
          and r.canApproveApplication = true
    """)
    Optional<StudyMember> findActiveApprover(@Param("studyId") long studyId,
                                             @Param("userId") long userId);

    // 도메인 B (Sprint 4) -----

    @Query("""
        select sm from StudyMember sm
        where sm.studyId = :studyId
          and sm.isActive = true
        order by sm.joinedAt asc
    """)
    List<StudyMember> findAllActiveByStudyIdOrderByJoinedAt(@Param("studyId") long studyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select sm from StudyMember sm
        where sm.id = :id and sm.studyId = :studyId
    """)
    Optional<StudyMember> findByIdAndStudyIdForUpdate(@Param("id") long id,
                                                     @Param("studyId") long studyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select sm from StudyMember sm
        where sm.studyId = :studyId
          and sm.userId  = :userId
          and sm.isActive = true
    """)
    Optional<StudyMember> findActiveByStudyIdAndUserIdForUpdate(@Param("studyId") long studyId,
                                                                @Param("userId") long userId);

    @Query("""
        select sm from StudyMember sm, com.studymate.application.domain.StudyRole r
        where r.code = sm.roleCode
          and sm.studyId = :studyId
          and sm.userId  = :userId
          and sm.isActive = true
          and r.canManageMember = true
    """)
    Optional<StudyMember> findActiveManager(@Param("studyId") long studyId,
                                            @Param("userId") long userId);

    // 도메인 C (Sprint 4) -----

    @Query("""
        select sm from StudyMember sm, com.studymate.application.domain.StudyRole r
        where r.code = sm.roleCode
          and sm.studyId = :studyId
          and sm.userId  = :userId
          and sm.isActive = true
          and r.canPostNotice = true
    """)
    Optional<StudyMember> findActiveNoticeWriter(@Param("studyId") long studyId,
                                                 @Param("userId") long userId);
}
