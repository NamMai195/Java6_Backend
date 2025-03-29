package com.backend.common;

public enum PaymentMethod {
    COD,           // Thanh toán khi nhận hàng
    BANK_TRANSFER, // Chuyển khoản ngân hàng
    CREDIT_CARD,   // Thẻ tín dụng (sẽ cần tích hợp cổng thanh toán)
    MOMO,          // Ví Momo
    VNPAY          // VNPay
    // Thêm các phương thức khác nếu cần
}
