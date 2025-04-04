// src/main/java/com/backend/model/AddressEntity.java
package com.backend.model;

import jakarta.persistence.*; // Đảm bảo import đúng
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.DynamicUpdate; // Import nếu bạn muốn dùng @DynamicUpdate
import java.util.Date;


@Entity
@Getter
@Setter
@Table(name = "tbl_address")
// @DynamicUpdate // Cân nhắc thêm annotation này để tối ưu UPDATE SQL
public class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apartment_number")
    private String apartmentNumber;

    private String floor;

    private String building;

    @Column(name = "street_number")
    private String streetNumber;

    private String street;

    // --- BỔ SUNG CÁC TRƯỜNG MỚI ---
    @Column(name = "ward") // Tên cột trong DB là 'ward'
    private String ward;      // Thuộc tính để lưu tên Phường/Xã

    @Column(name = "district") // Tên cột trong DB là 'district'
    private String district;  // Thuộc tính để lưu tên Quận/Huyện
    // --- KẾT THÚC BỔ SUNG ---

    // Trường city dùng để lưu Tên Tỉnh/Thành phố
    @Column(name = "city", nullable = false) // Giữ nullable=false nếu bắt buộc
    private String city;


    @Column(nullable = false)
    private String country;

    @Column(name = "address_type")
    private Integer addressType; // Giữ Integer theo yêu cầu

    // Quan hệ với UserEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // Timestamps
    @Column(name = "created_at", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;

    // Lombok sẽ tự tạo Constructor, Getter, Setter
}