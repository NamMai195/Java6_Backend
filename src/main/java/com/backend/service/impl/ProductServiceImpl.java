package com.backend.service.impl;

import com.backend.controller.request.ProductCreationRequest;
import com.backend.controller.request.ProductUpdateRequest;
import com.backend.controller.response.CategoryBasicResponse;
import com.backend.controller.response.ProductResponse;
import com.backend.exception.InvalidDataException;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.CategoryEntity;
import com.backend.model.ProductEntity;
import com.backend.model.ProductImageEntity; // Import ProductImageEntity
import com.backend.repository.CategoryRepository;
import com.backend.repository.ProductImageRepository; // Import ProductImageRepository
import com.backend.repository.ProductRepository;
import com.backend.service.ProductService;
import com.backend.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils; // Import CollectionUtils

import java.math.BigDecimal;
import java.util.ArrayList; // Import ArrayList
import java.util.Collections; // Import Collections
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "PRODUCT-SERVICE")
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository; // Inject ProductImageRepository

    // Helper method để map từ Entity sang Response DTO
    private ProductResponse mapToProductResponse(ProductEntity entity) {
        CategoryBasicResponse categoryResponse = null;
        if (entity.getCategory() != null) {
            categoryResponse = CategoryBasicResponse.builder()
                    .id(entity.getCategory().getId())
                    .name(entity.getCategory().getName())
                    .build();
        }

        // Lấy danh sách URL từ Set<ProductImageEntity>
        List<String> imageURLs = Collections.emptyList(); // Mặc định là list rỗng
        if (!CollectionUtils.isEmpty(entity.getImages())) { // Kiểm tra collection có rỗng không
            imageURLs = entity.getImages().stream()
                    .map(ProductImageEntity::getUrl) // Lấy URL từ mỗi ProductImageEntity
                    .collect(Collectors.toList());
        }

        return ProductResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .sku(entity.getSku())
                .stockQuantity(entity.getStockQuantity())
                //.imageUrl(entity.getImageUrl()) // <-- XÓA DÒNG NÀY
                .imageURLs(imageURLs) // <-- THÊM DÒNG NÀY
                .category(categoryResponse)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse createProduct(ProductCreationRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());

        if (productRepository.existsBySku(request.getSku())) {
            log.warn("SKU '{}' already exists.", request.getSku());
            throw new InvalidDataException("Product SKU '" + request.getSku() + "' already exists.");
        }

        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> {
                    log.warn("Category not found with ID: {}", request.getCategoryId());
                    return new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId());
                });

        // 3. Tạo ProductEntity (chưa có ảnh)
        ProductEntity product = new ProductEntity();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());
        product.setStockQuantity(request.getStockQuantity());
        // product.setImageUrl(request.getImageUrl()); // <-- XÓA DÒNG NÀY
        product.setCategory(category);

        // 4. Lưu ProductEntity lần đầu để lấy ID
        ProductEntity savedProduct = productRepository.save(product);
        log.info("Product entity saved with ID: {}", savedProduct.getId());

        // 5. Xử lý lưu các ảnh (ProductImageEntity)
        if (request.getImageURLs() != null && !request.getImageURLs().isEmpty()) {
            log.info("Saving {} images for product ID: {}", request.getImageURLs().size(), savedProduct.getId());
            List<ProductImageEntity> imagesToSave = new ArrayList<>();
            for (String imageUrl : request.getImageURLs()) {
                ProductImageEntity productImage = new ProductImageEntity();
                productImage.setUrl(imageUrl);
                productImage.setProduct(savedProduct); // Liên kết với product đã lưu
                // productImage.setIsPrimary(imagesToSave.isEmpty()); // Ví dụ: đặt ảnh đầu tiên là primary
                imagesToSave.add(productImage);
            }
            // Lưu tất cả ảnh vào DB
            productImageRepository.saveAll(imagesToSave);
            // Cập nhật lại set images trong product entity (không bắt buộc nếu không dùng ngay sau đó)
            // savedProduct.setImages(new HashSet<>(imagesToSave));
        } else {
            log.info("No images provided for product ID: {}", savedProduct.getId());
        }


        // 6. Nạp lại entity để có cả thông tin ảnh (hoặc map thủ công nếu cần)
        // Cách đơn giản nhất là fetch lại, nhưng có thể map thủ công nếu muốn tối ưu
        ProductEntity productWithImages = productRepository.findById(savedProduct.getId()).orElse(savedProduct);


        // 7. Map sang Response DTO và trả về
        return mapToProductResponse(productWithImages);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        log.info("Fetching product with ID: {}", productId);
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("Product not found with ID: {}", productId);
                    return new ResourceNotFoundException("Product not found with ID: " + productId);
                });
        // mapToProductResponse đã xử lý việc lấy list ảnh
        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {

        log.info("Fetching products with filters - keyword: [{}], categoryId: [{}], minPrice: [{}], maxPrice: [{}], page: {}, size: {}",
                keyword, categoryId, minPrice, maxPrice, pageable.getPageNumber(), pageable.getPageSize());

        // Xây dựng Specification dựa trên các tham số lọc
        Specification<ProductEntity> spec = Specification.where(null);
        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and(ProductSpecification.hasKeyword(keyword.trim()));
        }
        if (categoryId != null) {
            spec = spec.and(ProductSpecification.hasCategory(categoryId));
        }
        if (minPrice != null) {
            spec = spec.and(ProductSpecification.hasMinPrice(minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and(ProductSpecification.hasMaxPrice(maxPrice));
        }

        // Gọi Repository với Specification và Pageable
        // LƯU Ý: ProductRepository cần phải extends JpaSpecificationExecutor<ProductEntity, Long>
        Page<ProductEntity> productPage = productRepository.findAll(spec, pageable);

        // Chuyển đổi Page<ProductEntity> sang Page<ProductResponse> dùng hàm map của Page
        Page<ProductResponse> productResponsePage = productPage.map(this::mapToProductResponse);

        log.info("Found {} products matching criteria. Total pages: {}, Total elements: {}",
                productResponsePage.getNumberOfElements(), productResponsePage.getTotalPages(), productResponsePage.getTotalElements());

        return productResponsePage;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse updateProduct(Long productId, ProductUpdateRequest request) {
        log.info("Updating product with ID: {}", productId);

        ProductEntity existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("Update failed: Product not found with ID: {}", productId);
                    return new ResourceNotFoundException("Product not found with ID: " + productId);
                });

        if (!existingProduct.getSku().equalsIgnoreCase(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            log.warn("Update failed: New SKU '{}' already exists for another product.", request.getSku());
            throw new InvalidDataException("Product SKU '" + request.getSku() + "' already exists.");
        }

        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> {
                    log.warn("Update failed: Category not found with ID: {}", request.getCategoryId());
                    return new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId());
                });

        // 4. Cập nhật thông tin cơ bản cho existingProduct
        existingProduct.setName(request.getName());
        existingProduct.setDescription(request.getDescription());
        existingProduct.setPrice(request.getPrice());
        existingProduct.setSku(request.getSku());
        existingProduct.setStockQuantity(request.getStockQuantity());
        // existingProduct.setImageUrl(request.getImageUrl()); // <-- XÓA DÒNG NÀY
        existingProduct.setCategory(category);

        // 5. Xử lý cập nhật ảnh: Xóa cũ, thêm mới (Cách đơn giản)
        // Giả định có orphanRemoval=true trên ProductEntity.images
        log.info("Clearing existing images for product ID: {}", productId);
        existingProduct.getImages().clear(); // Xóa các ProductImageEntity cũ khỏi collection

        // Lưu product để kích hoạt orphanRemoval (hoặc xóa trực tiếp bằng repo nếu không dùng orphanRemoval)
        // productRepository.saveAndFlush(existingProduct); // Flush để xóa ngay lập tức nếu cần

        // Thêm ảnh mới từ request
        List<ProductImageEntity> newImages = new ArrayList<>();
        if (request.getImageURLs() != null && !request.getImageURLs().isEmpty()) {
            log.info("Adding {} new images for product ID: {}", request.getImageURLs().size(), productId);
            for(String imageUrl : request.getImageURLs()){
                ProductImageEntity newImage = new ProductImageEntity();
                newImage.setUrl(imageUrl);
                newImage.setProduct(existingProduct); // Liên kết với product đang cập nhật
                // newImage.setIsPrimary(newImages.isEmpty()); // Ví dụ: đặt ảnh đầu tiên là primary
                newImages.add(newImage);
            }
            existingProduct.getImages().addAll(newImages); // Thêm ảnh mới vào collection
        } else {
            log.info("No new images provided for update of product ID: {}", productId);
        }

        // 6. Lưu thay đổi (bao gồm cả ảnh mới nếu dùng CascadeType.ALL)
        ProductEntity updatedProduct = productRepository.save(existingProduct);
        log.info("Product updated successfully for ID: {}", updatedProduct.getId());

        // 7. Map sang Response DTO và trả về
        return mapToProductResponse(updatedProduct); // mapToProductResponse đã xử lý list ảnh
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long productId) {
        log.info("Deleting product with ID: {}", productId);

        // 1. Kiểm tra sản phẩm tồn tại
        // findById để lấy entity nếu bạn cần kiểm tra thêm trước khi xóa
        ProductEntity productToDelete = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("Delete failed: Product not found with ID: {}", productId);
                    throw new ResourceNotFoundException("Product not found with ID: " + productId);
                });

        // 2. Thực hiện xóa
        // CascadeType.ALL và orphanRemoval=true trên ProductEntity.images sẽ tự động xóa ProductImageEntity liên quan
        try {
            productRepository.delete(productToDelete); // Hoặc deleteById(productId)
            log.info("Product deleted successfully with ID: {}", productId);
        } catch (Exception e) {
            log.error("Error deleting product ID {}: {}", productId, e.getMessage());
            throw new RuntimeException("Could not delete product with ID: " + productId + ". It might be referenced elsewhere.", e);
        }
    }
}