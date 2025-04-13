package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class OrderItemResponse {
    private Long orderItemId;
    private Long productId;
    private String productName;
    private String productSku;
    private BigDecimal priceAtOrder;
    private Integer quantity;
    private BigDecimal subTotal;
    private String productImageUrl;
}
