package com.backend.controller.request;

import com.backend.common.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreationRequest {

    @NotNull(message = "Shipping Address ID is required")
    private Long shippingAddressId;

    // ID địa chỉ thanh toán, có thể null nếu giống địa chỉ giao hàng
    private Long billingAddressId;

    @NotNull(message = "Payment Method is required")
    private PaymentMethod paymentMethod; // Sử dụng Enum trực tiếp

    private String notes; // Ghi chú tùy chọn
}