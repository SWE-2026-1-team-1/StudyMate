package com.studymate.auth.repository;

import com.studymate.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    Optional<User> findByIdAndIsDeletedFalse(Long id);

    boolean existsByEmailAndIsDeletedFalse(String email);

    List<User> findAllByIdIn(Collection<Long> ids);
}
