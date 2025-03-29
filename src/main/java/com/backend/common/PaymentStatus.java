package com.backend.common;

public enum PaymentStatus {
    PENDING,       // Chờ thanh toán
    PAID,          // Đã thanh toán
    FAILED,        // Thanh toán thất bại
    REFUNDED       // Đã hoàn tiền
}
