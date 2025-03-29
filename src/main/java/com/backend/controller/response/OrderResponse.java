package com.backend.controller.response;

import com.backend.common.OrderStatus;
import com.backend.common.PaymentMethod;
import com.backend.common.PaymentStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
public class OrderResponse {
    private Long id;
    private String orderCode;
    private Long userId; // ID của người đặt hàng
    private Date orderDate;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private AddressResponse shippingAddress; // Thông tin địa chỉ giao hàng
    private AddressResponse billingAddress;  // Thông tin địa chỉ thanh toán (có thể null)
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String notes;
    private List<OrderItemResponse> orderItems; // Danh sách các sản phẩm trong đơn hàng
    private Date createdAt;
    private Date updatedAt;
}