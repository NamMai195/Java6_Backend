package com.backend.controller.request;

import com.backend.common.OrderStatus; // Import OrderStatus
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {

    @NotNull(message = "Order status is required")
    private OrderStatus status; // Trạng thái mới của đơn hàng
}