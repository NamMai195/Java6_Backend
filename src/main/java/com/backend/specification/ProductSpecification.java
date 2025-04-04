package com.backend.specification; // Giả sử package của bạn


import com.backend.model.CategoryEntity;
import com.backend.model.ProductEntity;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;

public class ProductSpecification {

    public static Specification<ProductEntity> hasKeyword(String keyword) {
        // Tìm kiếm keyword trong tên hoặc mô tả sản phẩm (không phân biệt hoa thường)
        return (root, query, criteriaBuilder) -> {
            String keywordLower = "%" + keyword.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), keywordLower),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), keywordLower)
            );
        };
    }

    public static Specification<ProductEntity> hasCategory(Long categoryId) {
        // Lọc theo ID của category (cần join với bảng Category)
        return (root, query, criteriaBuilder) -> {
            Join<ProductEntity, CategoryEntity> categoryJoin = root.join("category"); // Giả định tên quan hệ là "category"
            return criteriaBuilder.equal(categoryJoin.get("id"), categoryId);
        };
    }

    public static Specification<ProductEntity> hasMinPrice(BigDecimal minPrice) {
        // Lọc sản phẩm có giá >= minPrice
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<ProductEntity> hasMaxPrice(BigDecimal maxPrice) {
        // Lọc sản phẩm có giá <= maxPrice
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }
}