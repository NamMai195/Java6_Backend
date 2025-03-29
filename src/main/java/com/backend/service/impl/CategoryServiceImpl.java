package com.backend.service.impl;

import com.backend.controller.request.CategoryRequest;
import com.backend.controller.response.CategoryResponse;
import com.backend.exception.InvalidDataException;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.CategoryEntity;
import com.backend.repository.CategoryRepository;
import com.backend.repository.ProductRepository; // Inject để kiểm tra sản phẩm liên quan
import com.backend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException; // Bắt lỗi ràng buộc FK
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "CATEGORY-SERVICE")
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository; // Inject ProductRepository

    // Helper method để map Entity sang Response DTO
    private CategoryResponse mapToCategoryResponse(CategoryEntity entity) {
        if (entity == null) return null;
        return CategoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .parentCategoryId(entity.getParentCategory() != null ? entity.getParentCategory().getId() : null)
                .parentCategoryName(entity.getParentCategory() != null ? entity.getParentCategory().getName() : null)
                // Lấy danh sách con hoặc các thông tin khác nếu cần
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryResponse createCategory(CategoryRequest request) {
        log.info("Creating category with name: {}", request.getName());

        // 1. Kiểm tra tên trùng (ví dụ không phân biệt hoa thường)
        categoryRepository.findByNameIgnoreCase(request.getName()).ifPresent(existing -> {
            log.warn("Category name '{}' already exists.", request.getName());
            throw new InvalidDataException("Category name '" + request.getName() + "' already exists.");
        });

        CategoryEntity category = new CategoryEntity();
        category.setName(request.getName());
        category.setDescription(request.getDescription());

        // 2. Xử lý danh mục cha
        if (request.getParentCategoryId() != null) {
            CategoryEntity parentCategory = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> {
                        log.warn("Parent category not found with ID: {}", request.getParentCategoryId());
                        return new ResourceNotFoundException("Parent category not found with ID: " + request.getParentCategoryId());
                    });
            category.setParentCategory(parentCategory);
        } else {
            category.setParentCategory(null); // Là danh mục gốc
        }

        // 3. Lưu vào DB
        CategoryEntity savedCategory = categoryRepository.save(category);
        log.info("Category saved successfully with ID: {}", savedCategory.getId());

        // 4. Map và trả về
        return mapToCategoryResponse(savedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long categoryId) {
        log.info("Fetching category with ID: {}", categoryId);
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Category not found with ID: {}", categoryId);
                    return new ResourceNotFoundException("Category not found with ID: " + categoryId);
                });
        // Có thể fetch thêm subCategories nếu cần hiển thị chi tiết
        return mapToCategoryResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(Pageable pageable) {
        log.info("Fetching all categories page {} size {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<CategoryEntity> categoryPage = categoryRepository.findAll(pageable);

        // Map sang list response (flat list)
        List<CategoryResponse> responses = categoryPage.getContent().stream()
                .map(this::mapToCategoryResponse)
                .collect(Collectors.toList());
        log.info("Found {} categories on page {}", responses.size(), pageable.getPageNumber());
        return responses;
        // Nếu muốn trả về dạng cây, cần logic phức tạp hơn để query hoặc xử lý sau khi query
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        log.info("Updating category with ID: {}", categoryId);

        // 1. Tìm category hiện có
        CategoryEntity existingCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Update failed: Category not found with ID: {}", categoryId);
                    return new ResourceNotFoundException("Category not found with ID: " + categoryId);
                });

        // 2. Kiểm tra tên trùng (nếu tên thay đổi và trùng với category khác)
        if (!existingCategory.getName().equalsIgnoreCase(request.getName())) {
            categoryRepository.findByNameIgnoreCase(request.getName()).ifPresent(otherCategory -> {
                if (!otherCategory.getId().equals(categoryId)) { // Đảm bảo không phải chính nó
                    log.warn("Update failed: Category name '{}' already exists.", request.getName());
                    throw new InvalidDataException("Category name '" + request.getName() + "' already exists.");
                }
            });
        }

        // 3. Cập nhật thông tin
        existingCategory.setName(request.getName());
        existingCategory.setDescription(request.getDescription());

        // 4. Cập nhật danh mục cha
        if (request.getParentCategoryId() != null) {
            // Kiểm tra không thể đặt cha là chính nó hoặc con của nó (tránh vòng lặp)
            if (request.getParentCategoryId().equals(categoryId)) {
                log.warn("Update failed: Cannot set category's parent to itself (ID: {})", categoryId);
                throw new IllegalArgumentException("Cannot set category's parent to itself.");
            }
            // (Thêm logic kiểm tra phức tạp hơn nếu cần để tránh đặt cha là con của nó)

            CategoryEntity parentCategory = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> {
                        log.warn("Update failed: Parent category not found with ID: {}", request.getParentCategoryId());
                        return new ResourceNotFoundException("Parent category not found with ID: " + request.getParentCategoryId());
                    });
            existingCategory.setParentCategory(parentCategory);
        } else {
            existingCategory.setParentCategory(null); // Gỡ bỏ cha, trở thành danh mục gốc
        }

        // 5. Lưu thay đổi
        CategoryEntity updatedCategory = categoryRepository.save(existingCategory);
        log.info("Category updated successfully for ID: {}", updatedCategory.getId());

        // 6. Map và trả về
        return mapToCategoryResponse(updatedCategory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        log.info("Attempting to delete category with ID: {}", categoryId);

        // 1. Tìm category
        CategoryEntity categoryToDelete = categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Delete failed: Category not found with ID: {}", categoryId);
                    return new ResourceNotFoundException("Category not found with ID: " + categoryId);
                });

        // 2. Kiểm tra xem có danh mục con không
        // Nếu có con, không cho xóa trực tiếp (hoặc cần logic khác như gán con lên cấp cha,...)
        List<CategoryEntity> subCategories = categoryRepository.findByParentCategoryId(categoryId);
        if (!subCategories.isEmpty()) {
            log.warn("Delete failed: Category ID {} has sub-categories.", categoryId);
            throw new InvalidDataException("Cannot delete category with ID " + categoryId + " because it has sub-categories. Please delete or move sub-categories first.");
        }

        // 3. Kiểm tra xem có sản phẩm nào thuộc danh mục này không
        // Sử dụng ProductRepository (cần đảm bảo ProductEntity có reference đúng tới CategoryEntity)
        // Giả sử ProductEntity có trường 'category'
        long productCount = productRepository.countByCategory(categoryToDelete); // Cần thêm phương thức này vào ProductRepository
        if (productCount > 0) {
            log.warn("Delete failed: Category ID {} has associated products.", categoryId);
            throw new InvalidDataException("Cannot delete category with ID " + categoryId + " because it has associated products. Please move products to another category first.");
        }

        // 4. Nếu không có ràng buộc, thực hiện xóa
        try {
            categoryRepository.delete(categoryToDelete);
            log.info("Category deleted successfully with ID: {}", categoryId);
        } catch (DataIntegrityViolationException e) {
            // Trường hợp hy hữu nếu có ràng buộc khác chưa kiểm tra
            log.error("Data integrity violation while deleting category ID {}: {}", categoryId, e.getMessage());
            throw new RuntimeException("Could not delete category with ID: " + categoryId + " due to data integrity constraints.", e);
        } catch (Exception e) {
            log.error("Unexpected error deleting category ID: {}", categoryId, e);
            throw new RuntimeException("An unexpected error occurred while deleting category: " + categoryId, e);
        }
    }
}