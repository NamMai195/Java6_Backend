package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class OrderItemResponse {
    private Long orderItemId; // ID của OrderItemEntity
    private Long productId;
    private String productName;
    private String productSku; // Thêm Sku để dễ nhận biết
    private BigDecimal priceAtOrder; // Giá tại thời điểm đặt hàng
    private Integer quantity;
    private BigDecimal subTotal; // quantity * priceAtOrder
    // Có thể thêm imageUrl nếu cần
}