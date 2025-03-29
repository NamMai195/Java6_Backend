package com.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRequest { // Đổi tên để dùng chung

    @NotBlank(message = "Category name is required")
    @Size(max = 255, message = "Category name cannot exceed 255 characters")
    private String name;

    private String description;

    // ID của danh mục cha. Để null hoặc không gửi nếu là danh mục gốc.
    private Long parentCategoryId;
}