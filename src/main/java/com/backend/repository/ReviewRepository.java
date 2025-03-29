package com.backend.repository;

import com.backend.model.ReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    // Tìm tất cả review của một sản phẩm (có phân trang)
    Page<ReviewEntity> findByProductId(Long productId, Pageable pageable);

    // Tìm review của một user cụ thể cho một sản phẩm cụ thể (để kiểm tra trùng lặp)
    Optional<ReviewEntity> findByUserIdAndProductId(Long userId, Long productId);

    // Tùy chọn: Tìm review bằng ID và User ID (để kiểm tra quyền sở hữu khi xóa/sửa)
    Optional<ReviewEntity> findByIdAndUserId(Long reviewId, Long userId);
}