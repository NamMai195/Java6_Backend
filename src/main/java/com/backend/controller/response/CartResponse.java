package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
public class CartResponse {
    private Long cartId;
    private Long userId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount; // Tổng tiền của giỏ hàng
    private int totalItems; // Tổng số lượng sản phẩm (tính cả quantity)
}