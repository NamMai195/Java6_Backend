package com.backend.controller;

import com.backend.controller.request.SetInitialPasswordRequest;
import com.backend.controller.request.UserCreationRequest;
import com.backend.controller.request.UserPasswordRequest;
import com.backend.controller.request.UserUpdateRequest;
import com.backend.controller.response.UserResponse; // Import UserResponse
import com.backend.exception.ResourceNotFoundException;
import com.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest; // Import
import org.springframework.data.domain.Pageable;    // Import
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/users") // Sửa base path thành số nhiều và có version
@Tag(name = "User API v1") // Đổi tên Tag
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get Users", description = "Get a list of users with optional filtering and pagination")
    @GetMapping // Sửa path thành gốc
    public ResponseEntity<List<UserResponse>> getUsers( // Trả về ResponseEntity<List<UserResponse>>
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String username,
                                                        @RequestParam(required = false) String email,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        log.info("Request received to get users with params - keyword: {}, username: {}, email: {}, page: {}, size: {}", keyword, username, email, page, size);

        Pageable pageable = PageRequest.of(page, size);
        List<UserResponse> users;

        // Logic ưu tiên tìm kiếm
        if (username != null && !username.isEmpty()) {
            UserResponse user = userService.findByUserName(username);
            users = List.of(user);
        } else if (email != null && !email.isEmpty()) {
            UserResponse user = userService.findByEmail(email);
            users = List.of(user);
        } else {
            users = userService.findAll(keyword, pageable);
        }

        return ResponseEntity.ok(users);

    }

    // GET /api/v1/users/{userId}
    @Operation(summary = "Get User by ID", description = "Get detailed information for a specific user")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById( // Trả về ResponseEntity<UserResponse>
                                                     @PathVariable @Min(value = 1, message = "User ID must be greater than 0") Long userId) {
        log.info("Request received to get user detail for ID: {}", userId);
        UserResponse user = userService.findById(userId);
        return ResponseEntity.ok(user); // Trả về 200 OK với user data
    }

    // POST /api/v1/users
    @Operation(summary = "Register New User", description = "Create a new user account (pending verification)")
    @PostMapping // Sửa path thành gốc, dùng POST
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreationRequest userRequest) {
        log.info("Request received to create user: {}", userRequest.getUsername());
        long userId = userService.save(userRequest);
        UserResponse createdUser = userService.findById(userId);


        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(userId)
                .toUri();

        return ResponseEntity.created(location).body(createdUser);
    }

    @Operation(summary = "Update User Information", description = "Update details for an existing user")
    @PutMapping("/{userId}") // Thêm PathVariable userId, dùng PUT
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("Request received to update user ID: {}", userId);

        // Kiểm tra ID trong path và body có khớp không (quan trọng)
        if (!userId.equals(request.getId())) {
            log.error("User ID in path ({}) does not match ID in request body ({})", userId, request.getId());
            throw new IllegalArgumentException("User ID mismatch");
        }

        userService.update(request);
        UserResponse updatedUser = userService.findById(userId);

        return ResponseEntity.ok(updatedUser);
    }

    // PUT /api/v1/users/{userId}/password
    @Operation(summary = "Change User Password", description = "Set or change the password for a user")
    @PutMapping("/{userId}/password") // Dùng PUT và đường dẫn con /password
    public ResponseEntity<Void> changePassword( // Trả về ResponseEntity<Void>
                                                @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId,
                                                @Valid @RequestBody UserPasswordRequest request) {
        log.info("Request received to change password for user ID: {}", userId);

        // Kiểm tra ID khớp
        if (!userId.equals(request.getId())) {
            log.error("User ID in path ({}) does not match ID in request body ({})", userId, request.getId());
            throw new IllegalArgumentException("User ID mismatch");
        }

        userService.changePassword(request); // Gọi service đã sửa tên
        return ResponseEntity.noContent().build(); // Trả về 204 No Content khi thành công
    }

    // DELETE /api/v1/users/{userId}
    @Operation(summary = "Delete User (Soft)", description = "Mark a user account as inactive")
    @DeleteMapping("/{userId}") // Sửa path, dùng DELETE
    public ResponseEntity<Void> deleteUser( // Trả về ResponseEntity<Void>
                                            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId) { // Bỏ @Valid ở đây
        log.info("Request received to delete user ID: {}", userId);
        userService.delete(userId);
        return ResponseEntity.noContent().build(); // Trả về 204 No Content
    }

    @Operation(summary = "Verify Account", description = "Verify user account using the token sent via email.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Account verified successfully. You can now set your password."),
                    @ApiResponse(responseCode = "400", description = "Invalid or expired token.", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Token not found.", content = @Content)
            })
    @GetMapping("/verify") // Sử dụng GET vì đây là hành động idempotent qua link
    public ResponseEntity<Map<String, String>> verifyAccount(
            @Parameter(description = "Verification token received via email", required = true)
            @RequestParam @NotBlank(message = "Token cannot be blank") String token) {
        log.info("Request received to verify account with token: {}", token);
        try {
            // ** Cần thêm phương thức verifyAccount(String token) vào UserService **
            userService.verifyAccount(token);
            // Bạn có thể trả về một trang HTML xác nhận hoặc một thông báo JSON đơn giản
            return ResponseEntity.ok(Map.of("message", "Account verified successfully. Please set your password."));
        } catch (ResourceNotFoundException e) {
            log.warn("Verification failed: Token not found - {}", token);
            return ResponseEntity.status(404).body(Map.of("error", "Token not found."));
        } catch (IllegalArgumentException e) { // Hoặc một Exception tùy chỉnh khác cho token hết hạn/không hợp lệ
            log.warn("Verification failed: Invalid or expired token - {}", token, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); // Trả về thông báo lỗi từ service
        } catch (Exception e) {
            log.error("Unexpected error during account verification for token: {}", token, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "An unexpected error occurred during verification."));
        }
    }

    @Operation(summary = "Set Initial Password", description = "Set the initial password for a verified user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Password set successfully."),
                    @ApiResponse(responseCode = "400", description = "Invalid input (e.g., passwords don't match, user not verified, password already set).", content = @Content),
                    @ApiResponse(responseCode = "404", description = "User not found or not in a state to set password.", content = @Content)
            })
    @PostMapping("/set-initial-password") // Sử dụng POST để gửi mật khẩu
    public ResponseEntity<Map<String, String>> setInitialPassword(
            @Parameter(description = "Request body containing token/email and new password", required = true,
                    content = @Content(schema = @Schema(implementation = SetInitialPasswordRequest.class)))
            @Valid @RequestBody SetInitialPasswordRequest request) {
        // ** Cần tạo class SetInitialPasswordRequest với các trường cần thiết (vd: token hoặc email, password, confirmPassword) và validation **
        log.info("Request received to set initial password for user associated with: {}", request.getEmailOrToken()); // Giả sử request có getEmailOrToken()

        try {
            // ** Cần thêm phương thức setInitialPassword(SetInitialPasswordRequest request) vào UserService **
            userService.setInitialPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password set successfully. You can now log in."));
        } catch (ResourceNotFoundException e) {
            log.warn("Set initial password failed: User not found - {}", request.getEmailOrToken());
            return ResponseEntity.status(404).body(Map.of("error", "User not found or not ready for password setting."));
        } catch (IllegalStateException e) { // Dùng cho các lỗi trạng thái (vd: đã đặt mk, chưa verify)
            log.warn("Set initial password failed: Invalid state - {}", request.getEmailOrToken(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) { // Dùng cho lỗi validation (vd: mk không khớp)
            log.warn("Set initial password failed: Invalid input - {}", request.getEmailOrToken(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during setting initial password for: {}", request.getEmailOrToken(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "An unexpected error occurred while setting the password."));
        }
    }
}