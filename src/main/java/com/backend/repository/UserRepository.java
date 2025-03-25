package com.backend.repository;

import com.backend.controller.response.UserResponse;
import com.backend.model.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Page<UserEntity> findByFirstNameContainingOrLastNameContainingOrEmailContainingOrPhoneContainingOrUsernameContaining(String keyword, String keyword1, String keyword2, String keyword3, String keyword4, PageRequest pageable);
}
