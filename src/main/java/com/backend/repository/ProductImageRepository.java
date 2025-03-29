package com.backend.repository;

import com.backend.model.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List; // Import List nếu cần phương thức xóa theo product

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {
    // Tùy chọn: Thêm phương thức để xóa tất cả ảnh của một sản phẩm nếu không dùng orphanRemoval
    // void deleteByProductId(Long productId);
    // List<ProductImageEntity> findByProductId(Long productId); // Nếu cần lấy ảnh riêng
}