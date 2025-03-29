package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
public class ReviewResponse {
    private Long reviewId; // ID của ReviewEntity
    private Long productId;
    private UserBasicResponse user; // Thông tin cơ bản của người viết review
    private Integer rating; // Điểm đánh giá (1-5)
    private String comment;
    private Date createdAt;
    private Date updatedAt;
}