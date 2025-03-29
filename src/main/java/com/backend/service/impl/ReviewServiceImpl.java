package com.backend.service.impl;

import com.backend.controller.request.ReviewRequest;
import com.backend.controller.response.ReviewResponse;
import com.backend.controller.response.UserBasicResponse;
import com.backend.exception.InvalidDataException;
import com.backend.exception.ResourceNotFoundException;
//import com.backend.exception.UnauthorizedException; // Tạo exception này nếu cần
import com.backend.model.ProductEntity;
import com.backend.model.ReviewEntity;
import com.backend.model.UserEntity;
import com.backend.repository.ProductRepository;
import com.backend.repository.ReviewRepository;
import com.backend.repository.UserRepository;
import com.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException; // Để bắt lỗi unique constraint
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "REVIEW-SERVICE")
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // Helper method để map Entity sang Response DTO
    private ReviewResponse mapReviewToResponse(ReviewEntity entity) {
        if (entity == null) return null;

        UserBasicResponse userResponse = null;
        if (entity.getUser() != null) {
            userResponse = UserBasicResponse.builder()
                    .userId(entity.getUser().getId())
                    .username(entity.getUser().getUsername()) // Hoặc tên khác
                    .build();
        }

        return ReviewResponse.builder()
                .reviewId(entity.getId())
                .productId(entity.getProduct() != null ? entity.getProduct().getId() : null)
                .user(userResponse)
                .rating(entity.getRating())
                .comment(entity.getComment())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByProductId(Long productId, Pageable pageable) {
        log.info("Fetching reviews for product ID: {}, page: {}, size: {}", productId, pageable.getPageNumber(), pageable.getPageSize());
        // Kiểm tra sản phẩm tồn tại nếu cần
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }

        Page<ReviewEntity> reviewPage = reviewRepository.findByProductId(productId, pageable);
        log.info("Found {} reviews for product ID {} on page {}", reviewPage.getNumberOfElements(), productId, pageable.getPageNumber());

        List<ReviewResponse> reviewResponses = reviewPage.getContent().stream()
                .map(this::mapReviewToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(reviewResponses, pageable, reviewPage.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResponse createReview(Long userId, Long productId, ReviewRequest request) {
        log.info("User ID {} attempting to create review for product ID: {}", userId, productId);

        // 1. Kiểm tra xem user đã review sản phẩm này chưa
        reviewRepository.findByUserIdAndProductId(userId, productId).ifPresent(existing -> {
            log.warn("User ID {} already reviewed product ID {}", userId, productId);
            throw new InvalidDataException("You have already reviewed this product.");
        });

        // 2. Lấy thông tin User và Product
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        // TODO: Có thể thêm logic kiểm tra xem user đã mua sản phẩm này chưa nếu cần

        // 3. Tạo ReviewEntity
        ReviewEntity review = new ReviewEntity();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        // 4. Lưu vào DB
        try {
            ReviewEntity savedReview = reviewRepository.save(review);
            log.info("Review created successfully with ID: {}", savedReview.getId());
            return mapReviewToResponse(savedReview);
        } catch (DataIntegrityViolationException e) {
            // Xử lý trường hợp hy hữu bị race condition khi kiểm tra trùng lặp ở bước 1
            log.error("Data integrity violation while creating review for user {} product {}: {}", userId, productId, e.getMessage());
            throw new InvalidDataException("Failed to create review due to a conflict. Please try again.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(Long reviewId) {
        log.info("Fetching review with ID: {}", reviewId);
        ReviewEntity review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewId));
        return mapReviewToResponse(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResponse updateReview(Long reviewId, Long userId, ReviewRequest request) {
        log.info("User ID {} attempting to update review ID: {}", userId, reviewId);

        // 1. Tìm review và kiểm tra quyền sở hữu
        ReviewEntity existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewId));

        if (!existingReview.getUser().getId().equals(userId)) {
            log.error("User ID {} attempted to update review ID {} owned by user ID {}", userId, reviewId, existingReview.getUser().getId());
            // Có thể ném ForbiddenAccessException thay vì UnauthorizedException
            throw new UnauthorizedException("You are not authorized to update this review.");
        }

        // 2. Cập nhật thông tin
        existingReview.setRating(request.getRating());
        existingReview.setComment(request.getComment());

        // 3. Lưu thay đổi
        ReviewEntity updatedReview = reviewRepository.save(existingReview);
        log.info("Review ID {} updated successfully by user ID {}", reviewId, userId);

        return mapReviewToResponse(updatedReview);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long reviewId, Long userId) {
        log.info("User ID {} attempting to delete review ID: {}", userId, reviewId);

        // 1. Tìm review
        ReviewEntity reviewToDelete = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewId));

        // 2. Kiểm tra quyền sở hữu HOẶC quyền Admin
        // TODO: Thêm logic kiểm tra nếu user có role 'ADMIN' thì cho phép xóa
        boolean isAdmin = false; // Thay bằng logic kiểm tra role thực tế
        if (!reviewToDelete.getUser().getId().equals(userId) && !isAdmin) {
            log.error("User ID {} attempted to delete review ID {} owned by user ID {}", userId, reviewId, reviewToDelete.getUser().getId());
            throw new UnauthorizedException("You are not authorized to delete this review.");
        }

        // 3. Thực hiện xóa
        reviewRepository.delete(reviewToDelete);
        log.info("Review ID {} deleted successfully by user ID {} (or Admin)", reviewId, userId);
    }

    // Tạo lớp Exception tùy chỉnh nếu cần
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}