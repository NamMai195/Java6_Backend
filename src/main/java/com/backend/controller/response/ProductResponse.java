package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder // Sử dụng Builder pattern để dễ tạo đối tượng trong Service
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private Integer stockQuantity;
    private Date createdAt;
    private Date updatedAt;
    private List<String> imageURLs; // Danh sách URL ảnh trả về
    private CategoryBasicResponse category;
}