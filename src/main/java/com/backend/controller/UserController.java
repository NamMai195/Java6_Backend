package com.backend.controller;

import com.backend.controller.request.SetInitialPasswordRequest;
import com.backend.controller.request.UserCreationRequest;
import com.backend.controller.request.UserPasswordRequest;
import com.backend.controller.request.UserUpdateRequest;
import com.backend.controller.response.UserResponse;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.UserEntity;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus; // Import HttpStatus
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException; // Import AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority; // Import GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException; // Import ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects; // Import Objects

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User API v1")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    private Long getCurrentUserIdFromPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null; // Hoặc throw exception tùy logic
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserEntity currentUser) { // Sử dụng pattern matching
            return currentUser.getId();
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            // Nếu principal là UserDetails chuẩn, thử lấy username và tìm ID
            // Đoạn này cần logic riêng nếu bạn không cấu hình Principal là UserEntity
            log.warn("Principal is UserDetails, cannot directly get ID. Username: {}", userDetails.getUsername());
            // return findUserIdByUsername(userDetails.getUsername()); // Cần implement hàm này
            return null; // Hoặc throw lỗi nếu không lấy được ID
        }
        log.error("Could not determine User ID from principal type: {}", principal.getClass().getName());
        return null; // Hoặc throw lỗi
    }

    private boolean currentUserHasRole(String roleName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + roleName)); // Spring Security thường thêm tiền tố ROLE_
    }


    @Operation(summary = "Get Users", description = "Get list of users (Requires ADMIN role)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("ADMIN request received to get users with params - keyword: {}, page: {}, size: {}", keyword, page, size);
        Pageable pageable = PageRequest.of(page, size);
        List<UserResponse> users = userService.findAll(keyword, pageable);
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Get User by ID", description = "Get details for a specific user (Admin or the user themselves)")
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @userServiceImpl.findById(#userId).username == principal.username") // Ví dụ kiểm tra username nếu principal là UserDetails
    // Hoặc đơn giản hơn là chỉ cho phép ADMIN và user tự xem profile của mình (kiểm tra trong hàm)
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable @Min(value = 1, message = "User ID must be greater than 0") Long userId) {
        log.info("Request received to get user detail for ID: {}", userId);

        // Kiểm tra quyền truy cập trong code nếu @PreAuthorize phức tạp hoặc không đủ
        Long currentAuthUserId = getCurrentUserIdFromPrincipal();
        boolean isAdmin = currentUserHasRole("ADMIN");

        if (!isAdmin && !Objects.equals(userId, currentAuthUserId)) {
            log.warn("Access Denied: User {} attempting to access profile of user {}", currentAuthUserId, userId);
            throw new AccessDeniedException("You do not have permission to view this user's profile.");
            // Hoặc return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UserResponse user = userService.findById(userId);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Register New User", description = "Create a new user account (Public)")
    @PostMapping
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

    @Operation(summary = "Update User Information", description = "Update user details (Admin or the user themselves)")
    @PutMapping("/{userId}")
    // @PreAuthorize("hasRole('ADMIN') or #userId == principal.id") // Bỏ hoặc đơn giản hóa
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("Request received to update user ID: {}", userId);

        // --- Kiểm tra ID ---
        if (request.getId() == null || !userId.equals(request.getId())) {
            log.error("User ID in path ({}) does not match ID in request body ({}) or request ID is null", userId, request.getId());
            throw new IllegalArgumentException("User ID mismatch or missing in request body");
        }

        // --- Kiểm tra quyền ---
        Long currentAuthUserId = getCurrentUserIdFromPrincipal();
        boolean isAdmin = currentUserHasRole("ADMIN");

        if (!isAdmin && !Objects.equals(userId, currentAuthUserId)) {
            log.warn("Access Denied: User {} attempting to update profile of user {}", currentAuthUserId, userId);
            // Ném exception để global handler xử lý hoặc trả về lỗi trực tiếp
            throw new AccessDeniedException("You do not have permission to update this user's profile.");
            // Hoặc: return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Nếu là admin hoặc là user tự cập nhật profile của mình thì tiếp tục

        userService.update(request);
        UserResponse updatedUser = userService.findById(userId);

        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Change User Password", description = "Set or change the password (Admin or the user themselves)")
    @PutMapping("/{userId}/password")
    // @PreAuthorize("hasRole('ADMIN') or #userId == principal.id") // Bỏ hoặc đơn giản hóa
    public ResponseEntity<Void> changePassword(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId,
            @Valid @RequestBody UserPasswordRequest request) {
        log.info("Request received to change password for user ID: {}", userId);

        if (request.getId() == null || !userId.equals(request.getId())) {
            log.error("User ID in path ({}) does not match ID in request body ({}) or request ID is null", userId, request.getId());
            throw new IllegalArgumentException("User ID mismatch or missing in request body");
        }

        // --- Kiểm tra quyền ---
        Long currentAuthUserId = getCurrentUserIdFromPrincipal();
        boolean isAdmin = currentUserHasRole("ADMIN");

        if (!isAdmin && !Objects.equals(userId, currentAuthUserId)) {
            log.warn("Access Denied: User {} attempting to change password of user {}", currentAuthUserId, userId);
            throw new AccessDeniedException("You do not have permission to change this user's password.");
        }

        userService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete User (Soft)", description = "Mark user as inactive (Requires ADMIN role)")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId) {
        log.info("ADMIN Request received to delete user ID: {}", userId);
        userService.delete(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Verify Account", description = "Verify user account (Public)")
    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyAccount(
            @Parameter(description = "Verification token received via email", required = true)
            @RequestParam @NotBlank(message = "Token cannot be blank") String token) {
        log.info("Request received to verify account with token: {}", token);
        try {
            userService.verifyAccount(token);
            return ResponseEntity.ok(Map.of("message", "Account verified successfully. Please set your password."));
        } catch (ResourceNotFoundException e) {
            log.warn("Verification failed: Token not found - {}", token);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Token not found."));
        } catch (IllegalArgumentException e) {
            log.warn("Verification failed: Invalid or expired token - {}", token, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during account verification for token: {}", token, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred during verification."));
        }
    }

    @Operation(summary = "Set Initial Password", description = "Set initial password (Public)")
    @PostMapping("/set-initial-password")
    public ResponseEntity<Map<String, String>> setInitialPassword(
            @Parameter(description = "Request body containing token and new password", required = true,
                    content = @Content(schema = @Schema(implementation = SetInitialPasswordRequest.class)))
            @Valid @RequestBody SetInitialPasswordRequest request) {
        // Không log password ở đây
        log.info("Request received to set initial password using provided token.");

        try {
            userService.setInitialPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password set successfully. You can now log in."));
        } catch (ResourceNotFoundException e) {
            log.warn("Set initial password failed: User not found - Token might be invalid");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found or token invalid for password setting."));
        } catch (IllegalStateException e) {
            log.warn("Set initial password failed: Invalid state", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Set initial password failed: Invalid input", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during setting initial password.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred while setting the password."));
        }
    }
}
