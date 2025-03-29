package com.backend.repository;

import com.backend.model.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional; // Import Optional

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    // Tùy chọn: Tìm theo tên để kiểm tra trùng lặp (phân biệt hoa thường hoặc không)
    Optional<CategoryEntity> findByNameIgnoreCase(String name);

    // Tùy chọn: Tìm các danh mục gốc (không có danh mục cha)
    List<CategoryEntity> findByParentCategoryIsNull();

    // Tùy chọn: Tìm các danh mục con trực tiếp của một danh mục cha
    List<CategoryEntity> findByParentCategoryId(Long parentId);
}