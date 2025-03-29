package com.backend.service;

import com.backend.controller.request.ReviewRequest;
import com.backend.controller.response.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    // Lấy danh sách review cho một sản phẩm (phân trang)
    Page<ReviewResponse> getReviewsByProductId(Long productId, Pageable pageable);

    // Tạo review mới cho sản phẩm (cần userId và productId)
    ReviewResponse createReview(Long userId, Long productId, ReviewRequest request);

    // Lấy chi tiết một review cụ thể
    ReviewResponse getReviewById(Long reviewId);

    // Cập nhật review (cần userId để kiểm tra quyền sở hữu)
    ReviewResponse updateReview(Long reviewId, Long userId, ReviewRequest request);

    // Xóa review (cần userId để kiểm tra quyền sở hữu hoặc role Admin)
    void deleteReview(Long reviewId, Long userId);
}