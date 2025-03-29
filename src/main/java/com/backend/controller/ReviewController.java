package com.backend.controller;

import com.backend.controller.request.ReviewRequest;
import com.backend.controller.response.ReviewResponse;
import com.backend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Import Sort nếu cần sort
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Ví dụ phân quyền
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1") // Base path chung, các endpoint sẽ có /products/.../reviews hoặc /reviews/...
@Tag(name = "Review API v1", description = "APIs for managing product reviews")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    // --- Helper method để lấy User ID ---
    // !!! Quan trọng: Thay thế bằng logic lấy User ID thực tế !!!
    private Long getCurrentUserId() {
        log.warn("!!! Using hardcoded User ID 1 for Review operations. Replace with actual principal extraction. !!!");
        return 1L; // <<<< THAY THẾ LOGIC THỰC TẾ
        // throw new IllegalStateException("User ID could not be determined.");
    }
    // -----------------------------------

    @Operation(summary = "Get Reviews for Product", description = "Retrieves reviews for a specific product with pagination.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully"),
                    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
            })
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> getReviewsByProduct(
            @Parameter(description = "ID of the product to get reviews for", required = true)
            @PathVariable @Min(1) Long productId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of reviews per page") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort criteria (e.g., 'createdAt,desc' or 'rating,asc')")
            @RequestParam(defaultValue = "createdAt,desc") String sort) { // Ví dụ thêm sort

        log.info("Request received to get reviews for product ID: {}, page: {}, size: {}, sort: {}", productId, page, size, sort);
        // Xử lý Sort
        String[] sortParams = sort.split(",");
        Sort sortOrder = Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        Page<ReviewResponse> reviews = reviewService.getReviewsByProductId(productId, pageable);
        return ResponseEntity.ok(reviews);
    }

    @Operation(summary = "Create Review", description = "Adds a new review for a product by the current user. User can only review a product once.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Review created successfully",
                            content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input (e.g., rating out of range, already reviewed)", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Product or User not found", content = @Content)
            })
    @PostMapping("/products/{productId}/reviews")
    // @SecurityRequirement(name = "bearerAuth") // Yêu cầu đăng nhập
    public ResponseEntity<ReviewResponse> createReview(
            @Parameter(description = "ID of the product being reviewed", required = true)
            @PathVariable @Min(1) Long productId,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = getCurrentUserId();
        log.info("User ID {} attempting to create review for product ID: {} with rating {}", userId, productId, request.getRating());
        ReviewResponse createdReview = reviewService.createReview(userId, productId, request);
        // Tạo URI cho resource mới
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/api/v1/reviews/{id}") // Trỏ đến API lấy chi tiết review
                .buildAndExpand(createdReview.getReviewId())
                .toUri();
        log.info("Review created successfully with ID: {}", createdReview.getReviewId());
        return ResponseEntity.created(location).body(createdReview);
    }

    @Operation(summary = "Get Review by ID", description = "Retrieves details for a specific review.")
    // API này có thể public hoặc private tùy yêu cầu
    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> getReviewById(
            @PathVariable @Min(1) Long reviewId) {
        log.info("Request received to get review details for ID: {}", reviewId);
        ReviewResponse review = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(review);
    }


    @Operation(summary = "Update Review", description = "Updates an existing review written by the current user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Review updated successfully",
                            content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Forbidden (User does not own this review)", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Review not found", content = @Content)
            })
    @PutMapping("/reviews/{reviewId}")
    // @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable @Min(1) Long reviewId,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = getCurrentUserId();
        log.info("User ID {} attempting to update review ID: {}", userId, reviewId);
        ReviewResponse updatedReview = reviewService.updateReview(reviewId, userId, request);
        return ResponseEntity.ok(updatedReview);
    }


    @Operation(summary = "Delete Review", description = "Deletes a review. Can only be done by the user who wrote it or an admin.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Review deleted successfully", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Forbidden (User does not own this review and is not Admin)", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Review not found", content = @Content)
            })
    @DeleteMapping("/reviews/{reviewId}")
    // @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteReview(
            @PathVariable @Min(1) Long reviewId) {
        Long userId = getCurrentUserId(); // Lấy ID user để kiểm tra quyền hoặc xác định admin
        log.info("User ID {} (or Admin) attempting to delete review ID: {}", userId, reviewId);
        // Service sẽ kiểm tra quyền sở hữu hoặc quyền Admin bên trong
        reviewService.deleteReview(reviewId, userId);
        return ResponseEntity.noContent().build();
    }
}