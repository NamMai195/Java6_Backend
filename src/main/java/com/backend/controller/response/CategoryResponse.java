package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List; // Import List nếu muốn trả về danh sách con

@Getter
@Setter
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private Long parentCategoryId; // ID của danh mục cha (null nếu là gốc)
    private String parentCategoryName; // Tên danh mục cha (lấy từ service)
    // Tùy chọn: Trả về danh sách ID hoặc tên của các danh mục con trực tiếp
    // private List<Long> subCategoryIds;
    // private List<String> subCategoryNames;
    private Date createdAt;
    private Date updatedAt;

    // Không nên trả về list ProductEntity hoặc ProductResponse ở đây
    // để tránh vòng lặp và làm nặng response. Lấy sản phẩm theo category qua endpoint riêng.
}