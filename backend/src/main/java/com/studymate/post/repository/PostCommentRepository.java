package com.studymate.post.repository;

import com.studymate.post.domain.PostComment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    @Query("""
        select c from PostComment c
        where c.postId = :postId
          and c.isDeleted = false
        order by c.createdAt asc
    """)
    List<PostComment> findAllByPostIdAndNotDeletedOrderByCreatedAtAsc(@Param("postId") long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c from PostComment c
        where c.id = :id
          and c.postId = :postId
          and c.isDeleted = false
    """)
    Optional<PostComment> findByIdAndPostIdAndNotDeletedForUpdate(@Param("id") long id, @Param("postId") long postId);
}
