package com.backend.controller.response;

import com.backend.common.Gender;
import com.backend.common.UserStatus; // Import
import com.backend.common.UserType;   // Import
import lombok.*;

import java.util.Date;
import java.util.List;
// Import thêm nếu muốn trả về cả Address
// import java.util.List;
// import com.backend.controller.response.AddressResponse; // Giả sử có AddressResponse

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private Gender gender;
    private Date birthday;
    private String username;
    private String email;
    private String phone;
    // --- Thêm các trường ---
    private UserType type;
    private UserStatus status;
     private List<AddressResponse> addresses; // Tùy chọn: trả về danh sách địa chỉ
}