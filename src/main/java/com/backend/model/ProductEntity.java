// src/main/java/com/backend/model/ProductEntity.java
package com.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@Table(name = "tbl_products", indexes = {
        @Index(name = "idx_product_sku", columnList = "sku", unique = true) // Index cho SKU
})
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2) // Ví dụ: 10 chữ số, 2 chữ số thập phân
    private BigDecimal price;

    @Column(length = 100, unique = true) // SKU là duy nhất
    private String sku; // Stock Keeping Unit

    @Column(nullable = false, name = "stock_quantity")
    private Integer stockQuantity = 0; // Mặc định là 0

    // Quan hệ Một-Nhiều tới ProductImageEntity
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<ProductImageEntity> images = new HashSet<>();

    // Quan hệ nhiều-một với CategoryEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id") // Tên cột khóa ngoại
    private CategoryEntity category;

    // Quan hệ một-nhiều với OrderItemEntity
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private Set<OrderItemEntity> orderItems;

    // Quan hệ một-nhiều với CartItemEntity (nếu dùng)
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private Set<CartItemEntity> cartItems;

    // Quan hệ một-nhiều với ReviewEntity (nếu dùng)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ReviewEntity> reviews;

    @Column(name = "created_at", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;

    public Set<ProductImageEntity> getImages() {
        if (this.images == null) {
            this.images = new HashSet<>();
        }
        return images;
    }

    // Helper method để thêm ảnh (tùy chọn)
    public void addImage(ProductImageEntity image) {
        getImages().add(image);
        image.setProduct(this);
    }

    public void removeImage(ProductImageEntity image) {
        getImages().remove(image);
        image.setProduct(null);
    }
    public List<String> getImageURLs() {
        if (this.images == null || this.images.isEmpty()) {
            return Collections.emptyList();
        }
        return this.images.stream()
                .map(ProductImageEntity::getUrl) // Lấy URL từ mỗi ảnh
                .filter(Objects::nonNull)     // Bỏ qua nếu URL bị null
                .collect(Collectors.toList());
    }
}