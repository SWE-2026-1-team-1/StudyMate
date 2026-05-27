package com.studymate.post.repository;

import com.studymate.post.domain.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        select p from Post p
        where p.studyId = :studyId
          and p.isDeleted = false
        order by p.createdAt desc
    """)
    Page<Post> findAllByStudyIdAndNotDeleted(@Param("studyId") long studyId, Pageable pageable);

    @Query("""
        select p from Post p
        where p.id = :id
          and p.studyId = :studyId
          and p.isDeleted = false
    """)
    Optional<Post> findByIdAndStudyIdAndNotDeleted(@Param("id") long id, @Param("studyId") long studyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p from Post p
        where p.id = :id
          and p.studyId = :studyId
          and p.isDeleted = false
    """)
    Optional<Post> findByIdAndStudyIdAndNotDeletedForUpdate(@Param("id") long id, @Param("studyId") long studyId);
}
