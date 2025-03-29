package com.backend.model;

import com.backend.common.Gender;
import com.backend.common.UserStatus;
import com.backend.common.UserType;
import jakarta.persistence.*; // Đảm bảo import đúng
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.HashSet; // Import HashSet
import java.util.Set;    // Import Set

@Entity
@Getter
@Setter
@Table(name = "tbl_users", indexes = { // Thêm index nếu cần
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_verification_token", columnList = "verification_token", unique = true)
})
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "birthday")
    @Temporal(TemporalType.DATE)
    private Date birthday;

    @Column(name = "username", unique = true, nullable = false, length = 255)
    private String username;

    // Thêm unique=true, nullable=false cho email
    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    // --- SỬA Ở ĐÂY: Cho phép password là NULL ---
    @Column(name = "password", length = 255) // Bỏ nullable=false
    private String password;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private UserType type;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    // --- THÊM TRƯỜNG XÁC THỰC ---
    @Column(name = "verification_token", length = 100, unique = true)
    private String verificationToken;

    @Column(name = "token_expiry_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date tokenExpiryDate;

    // --- THÊM MỐI QUAN HỆ VÀ CASCADE ---
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<AddressEntity> addresses = new HashSet<>(); // Khởi tạo Set

    // (Thêm các mối quan hệ khác tới Order, Cart, Review nếu cần và đặt CascadeType phù hợp)
    // Ví dụ:
    // @OneToMany(mappedBy = "user", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    // private Set<OrderEntity> orders = new HashSet<>();
    // @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    // private CartEntity cart;


    @Column(name = "created_at", updatable = false) // Thêm updatable = false
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;
}