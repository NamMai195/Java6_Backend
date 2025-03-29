// src/main/java/com/backend/model/OrderEntity.java
package com.backend.model;

import com.backend.common.OrderStatus;
import com.backend.common.PaymentMethod;
import com.backend.common.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "tbl_orders", indexes = {
        @Index(name = "idx_order_code", columnList = "order_code", unique = true), // Index cho mã đơn hàng
        @Index(name = "idx_order_user_id", columnList = "user_id") // Index cho user
})
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", unique = true, nullable = false, length = 50)
    private String orderCode; // Nên tạo mã này ở tầng Service

    // Quan hệ nhiều-một với UserEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "order_date", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp // Tự động gán ngày giờ hiện tại khi tạo
    private Date orderDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING) // Lưu tên Enum vào DB
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING; // Trạng thái mặc định

    // Quan hệ nhiều-một với AddressEntity cho địa chỉ giao hàng
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id", nullable = false)
    private AddressEntity shippingAddress;

    // Quan hệ nhiều-một với AddressEntity cho địa chỉ thanh toán (có thể null nếu giống địa chỉ giao)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_address_id")
    private AddressEntity billingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING; // Mặc định

    @Column(columnDefinition = "TEXT")
    private String notes; // Ghi chú của khách hàng

    // Quan hệ một-nhiều với OrderItemEntity
    // CascadeType.ALL: Khi xóa Order thì xóa cả OrderItem liên quan
    // orphanRemoval = true: Khi xóa OrderItem khỏi collection này, nó cũng sẽ bị xóa khỏi DB
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<OrderItemEntity> orderItems;

    @Column(name = "created_at", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;

    // Callback để tự động tính tổng tiền trước khi lưu (ví dụ)
    // Bạn nên thực hiện logic này ở tầng Service để kiểm soát tốt hơn
    // @PrePersist
    // @PreUpdate
    // private void calculateTotalAmount() {
    //     if (orderItems != null) {
    //         this.totalAmount = orderItems.stream()
    //                                      .map(OrderItemEntity::getSubtotal)
    //                                      .reduce(BigDecimal.ZERO, BigDecimal::add);
    //     } else {
    //         this.totalAmount = BigDecimal.ZERO;
    //     }
    // }
}