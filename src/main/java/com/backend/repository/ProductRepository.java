package com.backend.repository;

import com.backend.model.CategoryEntity;
import com.backend.model.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // Thêm nếu cần tìm kiếm phức tạp
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> { // Thêm JpaSpecificationExecutor nếu muốn dùng Specification cho tìm kiếm

    // Kiểm tra xem SKU đã tồn tại chưa
    boolean existsBySku(String sku);

    // Tìm sản phẩm bằng SKU (có thể cần khi cập nhật)
    Optional<ProductEntity> findBySku(String sku);

    long countByCategory(CategoryEntity categoryToDelete);

    // Spring Data JPA tự tạo các phương thức CRUD cơ bản và phân trang (findAll(Pageable))
    // Bạn có thể thêm các @Query phức tạp hơn nếu cần
}