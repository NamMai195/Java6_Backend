package com.backend.controller.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List; // Import List

@Getter
@Setter
public class ProductCreationRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description; // Mô tả có thể không bắt buộc

    @NotNull(message = "Product price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    private BigDecimal price;

    @NotBlank(message = "Product SKU is required")
    @Size(max = 100, message = "SKU cannot exceed 100 characters")
    private String sku;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    // private String imageUrl; // <-- XÓA hoặc comment dòng này

    // Thêm trường này để nhận danh sách URL ảnh
    // @NotEmpty(message = "At least one image URL is required") // Bỏ NotEmpty nếu cho phép tạo sp không có ảnh
    private List<String> imageURLs;

    @NotNull(message = "Category ID is required")
    private Long categoryId; // ID của Category mà sản phẩm thuộc về
}