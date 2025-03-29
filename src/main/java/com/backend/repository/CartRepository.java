package com.backend.repository;

import com.backend.model.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<CartEntity, Long> {
    // Tìm giỏ hàng theo ID người dùng
    Optional<CartEntity> findByUserId(Long userId);
}