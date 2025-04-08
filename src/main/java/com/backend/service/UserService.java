package com.backend.service;

import com.backend.controller.request.SetInitialPasswordRequest;
import com.backend.controller.request.UserCreationRequest;
import com.backend.controller.request.UserPasswordRequest;
import com.backend.controller.request.UserUpdateRequest;
import com.backend.controller.response.UserResponse;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.UserEntity;
import org.springframework.data.domain.Pageable; // Import Pageable

import java.util.List;

public interface UserService {

    // Sửa: Dùng Pageable, sửa tên tham số keyword
    List<UserResponse> findAll(String keyword, Pageable pageable); // Đổi page, size thành Pageable

    UserResponse findById(Long id);
    UserResponse findByUserName(String userName);
    UserResponse findByEmail(String email);
    long save(UserCreationRequest req);
    void update(UserUpdateRequest req);

    // Giữ lại một hàm changePassword (đúng chuẩn camelCase)
    void changePassword(UserPasswordRequest req);

    void delete(Long id);

    void verifyAccount(String token) throws ResourceNotFoundException, IllegalArgumentException; // Throws exceptions để Controller bắt
    void setInitialPassword(SetInitialPasswordRequest request) throws ResourceNotFoundException, IllegalStateException, IllegalArgumentException; // Throws exceptions
    // Bỏ hàm void changePassword(UserPasswordRequest req); // Bỏ hàm trùng/rỗng
    /**
     * Xử lý user sau khi đăng nhập thành công qua OAuth2 (Google).
     * Tìm user bằng email, nếu không có thì tạo mới.
     * @param email Email lấy từ Google
     * @param firstName Tên lấy từ Google
     * @param lastName Họ lấy từ Google
     * @param imageUrl URL ảnh đại diện từ Google (có thể null)
     * @return UserEntity đã được lưu hoặc tìm thấy trong DB
     */
    UserEntity processOAuthPostLogin(String email, String firstName, String lastName, String imageUrl);
}