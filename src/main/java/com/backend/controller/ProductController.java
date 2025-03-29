package com.backend.controller;

import com.backend.controller.request.ProductCreationRequest; // ** Cần tạo **
import com.backend.controller.request.ProductUpdateRequest;   // ** Cần tạo **
import com.backend.controller.response.ProductResponse;     // ** Cần tạo **
// Không cần import ResourceNotFoundException ở đây nữa nếu không trực tiếp bắt
import com.backend.service.ProductService;             // ** Cần tạo **
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity; // Vẫn dùng ResponseEntity
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product API v1")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Create New Product", description = "Add a new product to the catalog.")
    @ApiResponse(responseCode = "201", description = "Product created successfully",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    // Các @ApiResponse cho lỗi 4xx, 5xx sẽ được xử lý bởi GlobalException handler
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductCreationRequest request) {
        log.info("Request received to create product with SKU: {}", request.getSku());
        // Gọi service trực tiếp, nếu có lỗi (vd: IllegalArgumentException) thì GlobalException handler sẽ bắt
        ProductResponse createdProduct = productService.createProduct(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdProduct.getId())
                .toUri();

        log.info("Product created successfully with ID: {}", createdProduct.getId());
        return ResponseEntity.created(location).body(createdProduct); // Trả về 201
    }

    @Operation(summary = "Get Product by ID", description = "Retrieve detailed information for a specific product.")
    @ApiResponse(responseCode = "200", description = "Product found",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    // Lỗi 404 (ResourceNotFoundException) sẽ do GlobalException handler xử lý
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable @Min(value = 1, message = "Product ID must be positive") Long productId) {
        log.info("Request received to get product detail for ID: {}", productId);
        // Gọi service trực tiếp, ResourceNotFoundException sẽ được handler bắt
        ProductResponse product = productService.getProductById(productId);
        return ResponseEntity.ok(product); // Trả về 200
    }

    @Operation(summary = "Get All Products", description = "Retrieve a list of products with optional filtering and pagination.")
    @ApiResponse(responseCode = "200", description = "List of products retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class))))
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Request received to get all products with params - keyword: {}, categoryId: {}, minPrice: {}, maxPrice: {}, page: {}, size: {}",
                keyword, categoryId, minPrice, maxPrice, page, size);
        Pageable pageable = PageRequest.of(page, size);

        // Gọi service trực tiếp
        List<ProductResponse> products = productService.getAllProducts(pageable); // Hoặc searchProducts(...)
        // Nếu service trả về Page, bạn có thể cần điều chỉnh kiểu trả về của endpoint hoặc xử lý Page object
        // ResponseEntity<Page<ProductResponse>> productPage = productService.searchProducts(...);

        return ResponseEntity.ok(products); // Trả về 200
    }

    @Operation(summary = "Update Product Information", description = "Update details for an existing product.")
    @ApiResponse(responseCode = "200", description = "Product updated successfully",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    // Lỗi 400, 404 sẽ do GlobalException handler xử lý
    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable @Min(value = 1, message = "Product ID must be positive") Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        log.info("Request received to update product ID: {}", productId);

        // Có thể vẫn giữ check ID mismatch ở đây vì nó là lỗi logic của request gửi lên
        // Hoặc chuyển logic này vào Service nếu muốn Controller gọn hơn nữa
        // Ví dụ: if (!productId.equals(request.getProductId())) { // Giả sử request có getProductId()
        //            throw new IllegalArgumentException("Product ID mismatch in path and body.");
        //        }

        // Gọi service trực tiếp
        ProductResponse updatedProduct = productService.updateProduct(productId, request);
        return ResponseEntity.ok(updatedProduct); // Trả về 200
    }

    @Operation(summary = "Delete Product", description = "Remove a product from the catalog.")
    @ApiResponse(responseCode = "204", description = "Product deleted successfully", content = @Content)
    // Lỗi 404 sẽ do GlobalException handler xử lý
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable @Min(value = 1, message = "Product ID must be positive") Long productId) {
        log.info("Request received to delete product ID: {}", productId);
        // Gọi service trực tiếp
        productService.deleteProduct(productId);
        log.info("Product deleted successfully with ID: {}", productId);
        return ResponseEntity.noContent().build(); // Trả về 204
    }
}