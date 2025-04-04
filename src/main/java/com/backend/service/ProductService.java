package com.backend.service;

import com.backend.controller.request.ProductCreationRequest;
import com.backend.controller.request.ProductUpdateRequest;
import com.backend.controller.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable; // Import Pageable

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    ProductResponse createProduct(ProductCreationRequest request);

    ProductResponse getProductById(Long productId);

    Page<ProductResponse> getAllProducts(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Có thể thêm phương thức tìm kiếm phức tạp hơn
    // Page<ProductResponse> searchProducts(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    ProductResponse updateProduct(Long productId, ProductUpdateRequest request);

    void deleteProduct(Long productId);
}