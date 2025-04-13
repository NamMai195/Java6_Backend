package com.backend.controller;

import com.backend.controller.request.ProductCreationRequest;
import com.backend.controller.request.ProductUpdateRequest;
import com.backend.controller.response.ProductResponse;
import com.backend.service.ProductService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Operation(summary = "Create New Product", description = "Add a new product to the catalog. (Requires ADMIN role)")
    @ApiResponse(responseCode = "201", description = "Product created successfully",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductCreationRequest request) {
        log.info("ADMIN Request received to create product with SKU: {}", request.getSku());
        ProductResponse createdProduct = productService.createProduct(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdProduct.getId())
                .toUri();

        log.info("Product created successfully with ID: {}", createdProduct.getId());
        return ResponseEntity.created(location).body(createdProduct);
    }

    @Operation(summary = "Get Product by ID", description = "Retrieve detailed information for a specific product. (Public Access)")
    @ApiResponse(responseCode = "200", description = "Product found",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable @Min(value = 1, message = "Product ID must be positive") Long productId) {
        log.info("Request received to get product detail for ID: {}", productId);
        ProductResponse product = productService.getProductById(productId);
        return ResponseEntity.ok(product);
    }
    @Operation(summary = "Get All Products", description = "Retrieve a list of products with filtering and pagination. (Public Access)")
    @ApiResponse(responseCode = "200", description = "List of products retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)))) // Adjust schema if returning Page
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Request received to get all products with params - keyword: {}, categoryId: {}, minPrice: {}, maxPrice: {}, page: {}, size: {}",
                keyword, categoryId, minPrice, maxPrice, page, size);

        Pageable pageable = PageRequest.of(page, size); // Create Pageable object

        Page<ProductResponse> productsPage = productService.getAllProducts(
                keyword, categoryId, minPrice, maxPrice, pageable
        );

        return ResponseEntity.ok(productsPage); // Return the Page object
    }

    @Operation(summary = "Update Product Information", description = "Update product details. (Requires ADMIN role)")
    @ApiResponse(responseCode = "200", description = "Product updated successfully",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable @Min(value = 1, message = "Product ID must be positive") Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        log.info("ADMIN Request received to update product ID: {}", productId);
        ProductResponse updatedProduct = productService.updateProduct(productId, request);
        return ResponseEntity.ok(updatedProduct);
    }

    @Operation(summary = "Delete Product", description = "Remove a product. (Requires ADMIN role)")
    @ApiResponse(responseCode = "204", description = "Product deleted successfully", content = @Content)
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable @Min(value = 1, message = "Product ID must be positive") Long productId) {
        log.info("ADMIN Request received to delete product ID: {}", productId);
        productService.deleteProduct(productId);
        log.info("Product deleted successfully with ID: {}", productId);
        return ResponseEntity.noContent().build();
    }
}
