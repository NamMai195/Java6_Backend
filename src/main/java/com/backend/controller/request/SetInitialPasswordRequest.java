package com.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter // Thêm Setter nếu cần thiết, ví dụ cho binding từ request body
public class SetInitialPasswordRequest implements Serializable {

    // Chọn MỘT trong hai: token hoặc email để xác định người dùng
    // Tùy thuộc vào cách bạn triển khai logic trong service
    // Ví dụ: Dùng token nếu bước verify và set password là riêng biệt
    @NotBlank(message = "Token cannot be blank")
    private String token;

    // Hoặc dùng email nếu bạn muốn người dùng nhập email của họ
    // @NotBlank(message = "Email cannot be blank")
    // @jakarta.validation.constraints.Email(message = "Invalid email format")
    // private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long") // Thêm ràng buộc độ dài nếu muốn
    private String password;

    @NotBlank(message = "Confirm Password is required")
    private String confirmPassword;

    // Phương thức helper để lấy định danh (tùy chọn, dựa vào cách bạn dùng ở Controller/Service)
    public String getEmailOrToken() {
        // if (email != null) return email;
        return token; // Trả về token nếu dùng token
    }
}
