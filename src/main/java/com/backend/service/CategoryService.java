package com.backend.service;

import com.backend.controller.request.CategoryRequest;
import com.backend.controller.response.CategoryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse getCategoryById(Long categoryId);

    // Trả về List phẳng, có thể thêm phương thức lấy dạng cây nếu cần
    List<CategoryResponse> getAllCategories(Pageable pageable);

    CategoryResponse updateCategory(Long categoryId, CategoryRequest request);

    void deleteCategory(Long categoryId);
}