package com.backend.common;

public enum OrderStatus {
    PENDING,       // Chờ xử lý
    PROCESSING,    // Đang xử lý
    SHIPPED,       // Đã giao hàng
    DELIVERED,     // Đã nhận hàng
    CANCELLED,     // Đã hủy
    FAILED         // Thất bại
}
