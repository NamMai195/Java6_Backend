package com.backend.repository;

import com.backend.common.UserStatus;
import com.backend.model.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Bỏ import java.lang.ScopedValue; // Không cần thiết
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // --- Query tìm kiếm (giữ nguyên) ---
    @Query("SELECT u FROM UserEntity u WHERE " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND u.status = :status")
    Page<UserEntity> searchUsersByKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            Pageable pageable);

    // --- Các phương thức khác (giữ nguyên) ---
    Page<UserEntity> findByStatus(UserStatus userStatus, Pageable pageable);

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // --- SỬA PHƯƠNG THỨC NÀY ---
    // Phương thức tìm User bằng verificationToken
    Optional<UserEntity> findByVerificationToken(String token); // Sửa kiểu trả về thành Optional<UserEntity>
}