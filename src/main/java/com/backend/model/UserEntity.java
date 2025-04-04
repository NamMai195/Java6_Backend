package com.backend.model;

import com.backend.common.Gender;
import com.backend.common.UserStatus;
import com.backend.common.UserType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
// **THÊM IMPORT NÀY**
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.*;

@Entity
@Getter
@Setter
@Table(name = "tbl_users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_verification_token", columnList = "verification_token", unique = true)
})
public class UserEntity implements UserDetails, Serializable {

    // ... (Các trường khác giữ nguyên: id, firstName, ..., updatedAt) ...
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

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private UserType type; // Trường này quyết định quyền

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "verification_token", length = 100, unique = true)
    private String verificationToken;

    @Column(name = "token_expiry_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date tokenExpiryDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<AddressEntity> addresses = new HashSet<>();


    @Column(name = "created_at", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;


    // === SỬA PHƯƠNG THỨC NÀY ===
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Trả về danh sách quyền dựa trên trường 'type'
        // Spring Security thường mong đợi quyền có tiền tố "ROLE_"
        if (this.type != null) {
            // Ví dụ: nếu type là UserType.ADMIN -> trả về role "ROLE_ADMIN"
            //         nếu type là UserType.USER -> trả về role "ROLE_USER"
            return List.of(new SimpleGrantedAuthority("ROLE_" + this.type.name()));
        }
        // Nếu không có type (không nên xảy ra), trả về danh sách rỗng
        return List.of();
    }
    // ============================

    @Override
    public boolean isAccountNonExpired() {
        // Mặc định là true, có thể thêm logic kiểm tra nếu cần
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Mặc định là true, có thể thêm logic kiểm tra nếu cần
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // Mặc định là true, mật khẩu không tự hết hạn
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Chỉ cho phép user hoạt động nếu status là ACTIVE
        return UserStatus.ACTIVE.equals(this.status);
    }
}