package com.backend.controller;

import com.backend.controller.request.ReviewRequest;
import com.backend.controller.response.ReviewResponse;
import com.backend.model.UserEntity;
import com.backend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
// Import nếu cần SecurityRequirement
// import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
// **THÊM IMPORT CHO PHÂN QUYỀN VÀ SECURITY CONTEXT**
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
// Import Principal/UserDetails nếu cần
// import com.backend.model.UserEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Review API v1", description = "APIs for managing product reviews")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    // --- Helper lấy User ID (Ví dụ - Cần điều chỉnh theo Principal thực tế) ---
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            log.error("User not authenticated for this operation.");
            throw new IllegalStateException("User must be authenticated to perform this operation.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserEntity) {
            UserEntity currentUser = (UserEntity) principal;
            // log.debug("Authenticated User ID: {}", currentUser.getId()); // Bỏ log debug nếu không cần
            return currentUser.getId(); // Trả về ID thực sự
        } else {
            // Nếu principal không phải là UserEntity, báo lỗi
            log.error("Could not determine User ID from principal type: {}", principal.getClass().getName());
            throw new IllegalStateException("Could not determine User ID from principal.");
        }
    }
    // ------------------------------------------------------------------------


    @Operation(summary = "Get Reviews for Product", description = "Retrieves reviews for a specific product with pagination.")
    @GetMapping("/products/{productId}/reviews")
    // Mọi người dùng đã đăng nhập đều có thể xem reviews
    public ResponseEntity<Page<ReviewResponse>> getReviewsByProduct(
            @PathVariable @Min(1) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        log.info("Request received to get reviews for product ID: {}, page: {}, size: {}, sort: {}", productId, page, size, sort);
        String[] sortParams = sort.split(",");
        Sort sortOrder = Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        Page<ReviewResponse> reviews = reviewService.getReviewsByProductId(productId, pageable);
        return ResponseEntity.ok(reviews);
    }

    @Operation(summary = "Create Review", description = "Adds a new review for a product by the current user.")
    @PostMapping("/products/{productId}/reviews")
    // Mọi người dùng đã đăng nhập đều có thể tạo review (USER hoặc ADMIN)
    // @SecurityRequirement(name = "bearerAuth") // Đánh dấu yêu cầu token
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable @Min(1) Long productId,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = getCurrentUserId();
        log.info("User ID {} attempting to create review for product ID: {} with rating {}", userId, productId, request.getRating());
        ReviewResponse createdReview = reviewService.createReview(userId, productId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/api/v1/reviews/{id}")
                .buildAndExpand(createdReview.getReviewId())
                .toUri();
        log.info("Review created successfully with ID: {}", createdReview.getReviewId());
        return ResponseEntity.created(location).body(createdReview);
    }

    @Operation(summary = "Get Review by ID", description = "Retrieves details for a specific review.")
    @GetMapping("/reviews/{reviewId}")
    // Mọi người dùng đã đăng nhập đều có thể xem review chi tiết
    public ResponseEntity<ReviewResponse> getReviewById(
            @PathVariable @Min(1) Long reviewId) {
        log.info("Request received to get review details for ID: {}", reviewId);
        ReviewResponse review = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(review);
    }


    @Operation(summary = "Update Review", description = "Updates an existing review (Owner or ADMIN)")
    @PutMapping("/reviews/{reviewId}")
    // **PHÂN QUYỀN: Chỉ ADMIN hoặc người sở hữu mới được sửa**
    // Tạm thời chỉ cho ADMIN. Cần bổ sung logic kiểm tra quyền sở hữu sau.
    @PreAuthorize("hasRole('ADMIN')")
    // @PreAuthorize("hasRole('ADMIN') or @reviewServiceImpl.isOwner(#reviewId, principal.id)") // Cách nâng cao
    // @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable @Min(1) Long reviewId,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = getCurrentUserId(); // Lấy userId hiện tại để service kiểm tra quyền sở hữu (nếu không dùng SpEL phức tạp)
        log.info("User ID {} (or Admin) attempting to update review ID: {}", userId, reviewId);
        // Service sẽ cần kiểm tra userId này có phải là chủ review không (nếu không phải ADMIN)
        ReviewResponse updatedReview = reviewService.updateReview(reviewId, userId, request);
        return ResponseEntity.ok(updatedReview);
    }


    @Operation(summary = "Delete Review", description = "Deletes a review (Owner or ADMIN)")
    @DeleteMapping("/reviews/{reviewId}")
    // **PHÂN QUYỀN: Chỉ ADMIN hoặc người sở hữu mới được xóa**
    // Tạm thời chỉ cho ADMIN. Cần bổ sung logic kiểm tra quyền sở hữu sau.
    @PreAuthorize("hasRole('ADMIN')")
    // @PreAuthorize("hasRole('ADMIN') or @reviewServiceImpl.isOwner(#reviewId, principal.id)") // Cách nâng cao
    // @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteReview(
            @PathVariable @Min(1) Long reviewId) {
        Long userId = getCurrentUserId(); // Lấy userId hiện tại để service kiểm tra quyền sở hữu (nếu không phải ADMIN)
        log.info("User ID {} (or Admin) attempting to delete review ID: {}", userId, reviewId);
        // Service sẽ cần kiểm tra userId này có phải là chủ review không (nếu không phải ADMIN)
        reviewService.deleteReview(reviewId, userId);
        return ResponseEntity.noContent().build();
    }
}