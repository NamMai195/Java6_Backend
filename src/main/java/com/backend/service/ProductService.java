package com.backend.service;

import com.backend.controller.request.ProductCreationRequest;
import com.backend.controller.request.ProductUpdateRequest;
import com.backend.controller.response.ProductResponse;
import org.springframework.data.domain.Pageable; // Import Pageable

import java.util.List;

public interface ProductService {

    ProductResponse createProduct(ProductCreationRequest request);

    ProductResponse getProductById(Long productId);

    // Trả về List để đơn giản, có thể đổi thành Page<ProductResponse> nếu cần phân trang đầy đủ
    List<ProductResponse> getAllProducts(Pageable pageable);

    // Có thể thêm phương thức tìm kiếm phức tạp hơn
    // Page<ProductResponse> searchProducts(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    ProductResponse updateProduct(Long productId, ProductUpdateRequest request);

    void deleteProduct(Long productId);
}