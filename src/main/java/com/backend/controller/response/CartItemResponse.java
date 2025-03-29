package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private BigDecimal productPrice; // Giá của 1 sản phẩm tại thời điểm xem giỏ hàng
    private String productImageUrl; // Lấy ảnh đầu tiên hoặc ảnh chính
    private Integer quantity;
    private BigDecimal subTotal; // quantity * productPrice
}