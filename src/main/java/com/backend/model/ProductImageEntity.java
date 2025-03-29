package com.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "tbl_product_images")
public class ProductImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500) // URL của ảnh
    private String url;

    @Column(name = "alt_text", length = 255) // Mô tả ảnh (tùy chọn)
    private String altText;

    @Column(name = "is_primary") // Đánh dấu ảnh chính (tùy chọn)
    private Boolean isPrimary = false;

    // Quan hệ Nhiều-Một với ProductEntity
    @ManyToOne(fetch = FetchType.LAZY, optional = false) // Bắt buộc phải thuộc về 1 product
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    // Không cần timestamp ở đây nếu không cần quản lý riêng
}