// src/main/java/com/backend/model/AddressEntity.java
package com.backend.model;

import jakarta.persistence.*; // Đảm bảo import đúng
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.util.Date;
// Import Set nếu cần khai báo quan hệ ngược lại từ OrderEntity
// import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "tbl_address")
public class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apartment_number") // Sử dụng @Column nếu muốn tên cột khác tên thuộc tính
    private String apartmentNumber;

    private String floor;

    private String building;

    @Column(name = "street_number")
    private String streetNumber;

    private String street;

    // Xem xét thêm nullable=false nếu city, country là bắt buộc
    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @Column(name = "address_type")
    private Integer addressType; // Giữ Integer theo yêu cầu

    // === THAY ĐỔI QUAN TRỌNG Ở ĐÂY ===
    @ManyToOne(fetch = FetchType.LAZY) // Mối quan hệ nhiều địa chỉ -> một user
    @JoinColumn(name = "user_id", nullable = false) // Cột khóa ngoại trong tbl_address là user_id, không được null
    private UserEntity user;

    // (Các quan hệ @OneToMany nếu có từ OrderEntity)

    @Column(name = "created_at", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;
}