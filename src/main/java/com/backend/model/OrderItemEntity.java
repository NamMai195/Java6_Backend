// src/main/java/com/backend/model/OrderItemEntity.java
package com.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "tbl_order_items")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ nhiều-một với OrderEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    // Quan hệ nhiều-một với ProductEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_at_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrder; // Giá tại thời điểm đặt hàng

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal; // Tổng tiền cho item này (quantity * priceAtOrder)

    // Callback để tự động tính subtotal (cũng nên làm ở Service)
    // @PrePersist
    // @PreUpdate
    // private void calculateSubtotal() {
    //     if (priceAtOrder != null && quantity != null) {
    //         this.subtotal = priceAtOrder.multiply(BigDecimal.valueOf(quantity));
    //     } else {
    //         this.subtotal = BigDecimal.ZERO;
    //     }
    // }

    // Không cần createdAt/updatedAt riêng cho item, vì nó phụ thuộc vào Order
}