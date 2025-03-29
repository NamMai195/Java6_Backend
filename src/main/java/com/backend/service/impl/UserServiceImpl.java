package com.backend.service.impl;

import com.backend.common.UserStatus;
import com.backend.controller.request.*;
import com.backend.controller.response.UserResponse;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.AddressEntity;
import com.backend.model.UserEntity;
import com.backend.repository.AddressRepository;
import com.backend.repository.UserRepository;
import com.backend.service.BrevoEmailService; // Import BrevoEmailService
import com.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // Import @Value
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import sendinblue.ApiException; // Import ApiException

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "USER-SERVICE")
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final BrevoEmailService emailService; // Inject BrevoEmailService

    // Inject các giá trị cấu hình từ application.yml/properties
    // Đảm bảo các properties này tồn tại trong file cấu hình của bạn (vd: application-dev.yml)
    @Value("${app.base-url:http://localhost:8080}") // URL cơ sở của ứng dụng để tạo link xác thực
    private String appBaseUrl;

    @Value("${app.email.verification-template-id:1}") // ID của template email xác thực trên Brevo
    private Long verificationTemplateId;

    // Inject các giá trị mặc định khác nếu template của bạn yêu cầu
    // Ví dụ: Lấy các giá trị tương tự như trong EmailController
    @Value("${app.email.defaults.service-name:My Application}")
    private String defaultServiceName;

    @Value("${app.email.defaults.company-name:My Company Inc.}")
    private String defaultCompanyName;

    // ... (Inject các giá trị @Value khác nếu cần cho template)

    // --- Các phương thức khác giữ nguyên ---
    private UserResponse mapToUserResponse(UserEntity userEntity) {
        // ... (giữ nguyên)
        return UserResponse.builder()
                .id(userEntity.getId())
                .firstName(userEntity.getFirstName())
                .lastName(userEntity.getLastName())
                .gender(userEntity.getGender())
                .birthday(userEntity.getBirthday())
                .username(userEntity.getUsername())
                .phone(userEntity.getPhone())
                .email(userEntity.getEmail())
                .type(userEntity.getType())
                .status(userEntity.getStatus())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll(String keyword, Pageable pageable) {
        // ... (giữ nguyên)
        Page<UserEntity> userPage;

        if (StringUtils.hasText(keyword)) {
            log.info("Finding users with keyword '{}', page {}, size {}", keyword, pageable.getPageNumber(), pageable.getPageSize());
            userPage = userRepository.searchUsersByKeywordAndStatus(keyword, UserStatus.ACTIVE, pageable);
        } else {
            log.info("Finding all active users, page {}, size {}", pageable.getPageNumber(), pageable.getPageSize());
            userPage = userRepository.findByStatus(UserStatus.ACTIVE, pageable); // Gọi hàm repo đã sửa
        }

        return userPage.getContent().stream()
                .map(this::mapToUserResponse) // Dùng hàm map helper
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByUserName(String userName) {
        // ... (giữ nguyên)
        log.info("Finding user by username: {}", userName);
        UserEntity user = userRepository.findByUsername(userName)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + userName));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        // ... (giữ nguyên)
        log.info("Finding user by id: {}", id);
        UserEntity userEntity = getUserById(id); // Dùng hàm helper
        return mapToUserResponse(userEntity); // Dùng hàm map helper
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        // ... (giữ nguyên)
        log.info("Finding user by email: {}", email);
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToUserResponse(user);
    }


    // --- CẬP NHẬT PHƯƠNG THỨC SAVE ---
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long save(UserCreationRequest req) {
        log.info("Registering new user: {}", req.getUsername());

        // --- Các bước kiểm tra và tạo UserEntity (giữ nguyên như code hiện tại của bạn) ---
        if (userRepository.existsByUsername(req.getUsername())) {
            log.warn("Username {} already exists.", req.getUsername());
            throw new IllegalArgumentException("Username '" + req.getUsername() + "' already exists");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            log.warn("Email {} already exists.", req.getEmail());
            throw new IllegalArgumentException("Email '" + req.getEmail() + "' already exists");
        }

        UserEntity user = new UserEntity();
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setGender(req.getGender());
        user.setBirthday(req.getBirthday());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setType(req.getType());
        user.setPassword(null); // Mật khẩu sẽ được đặt sau khi xác thực
        user.setStatus(UserStatus.PENDING_VERIFICATION); // Trạng thái chờ xác thực

        // Tạo token và ngày hết hạn (đã có trong code của bạn)
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setTokenExpiryDate(Date.from(Instant.now().plus(Duration.ofHours(24)))); // Ví dụ: Hết hạn sau 24 giờ
        log.info("Generated verification token for user {}: {}", user.getUsername(), token);

        // Xử lý địa chỉ (đã có trong code của bạn)
        Set<AddressEntity> addresses = new HashSet<>();
        if (req.getAddresses() != null && !req.getAddresses().isEmpty()) {
            log.info("Processing {} addresses for new user {}", req.getAddresses().size(), req.getUsername());
            for (AddressRequest addressReq : req.getAddresses()) {
                AddressEntity addressEntity = new AddressEntity();
                // ... (set các thuộc tính cho addressEntity)
                addressEntity.setApartmentNumber(addressReq.getApartmentNumber());
                addressEntity.setFloor(addressReq.getFloor());
                addressEntity.setBuilding(addressReq.getBuilding());
                addressEntity.setStreetNumber(addressReq.getStreetNumber());
                addressEntity.setStreet(addressReq.getStreet());
                addressEntity.setCity(addressReq.getCity());
                addressEntity.setCountry(addressReq.getCountry());
                addressEntity.setAddressType(addressReq.getAddressType());
                addressEntity.setUser(user); // Quan trọng: Liên kết địa chỉ với user
                addresses.add(addressEntity);
            }
        }
        user.setAddresses(addresses); // Gán danh sách địa chỉ vào user

        // Lưu user vào database (Cascade sẽ tự động lưu cả addresses)
        UserEntity savedUser = userRepository.save(user);
        log.info("Saved user pending verification with ID: {}", savedUser.getId());

        // --- GỬI EMAIL XÁC THỰC ---
        try {
            // Tạo link xác thực (cần endpoint để xử lý việc này, ví dụ /api/v1/auth/verify)
            String verificationLink = appBaseUrl + "/api/v1/users/verify?token=" + savedUser.getVerificationToken();

            // Chuẩn bị các tham số cho email template
            Map<String, Object> emailParams = new HashMap<>();
            emailParams.put("user_name", savedUser.getFirstName()); // Hoặc getFullName() nếu có
            emailParams.put("verification_link", verificationLink);
            // Thêm các tham số mặc định khác mà template có thể cần
            emailParams.put("service_name", defaultServiceName);
            emailParams.put("company_name", defaultCompanyName);
            // ... (Thêm các tham số khác nếu cần, ví dụ: login_link, faq_link, policy_link...)

            log.info("Attempting to send verification email to {} using template ID {}", savedUser.getEmail(), verificationTemplateId);

            // Gọi BrevoEmailService để gửi email bằng template
            emailService.sendEmailWithTemplate(
                    savedUser.getEmail(),
                    verificationTemplateId, // Sử dụng template ID đã inject
                    emailParams
            );

            log.info("Verification email sent successfully to {}", savedUser.getEmail());

        } catch (ApiException e) {
            // Xử lý lỗi khi gửi email (quan trọng: không nên để lỗi này làm rollback việc tạo user)
            // Log lỗi chi tiết để debug
            log.error("Brevo API Exception while sending verification email to {}: Status Code: {}, Response Body: {}",
                    savedUser.getEmail(), e.getCode(), e.getResponseBody(), e);
            // Có thể thông báo cho hệ thống monitoring hoặc quản trị viên
            // Không throw lại exception ở đây để tránh rollback transaction tạo user
        } catch (Exception e) {
            // Bắt các lỗi không mong muốn khác
            log.error("Unexpected error while sending verification email to {}: {}",
                    savedUser.getEmail(), e.getMessage(), e);
            // Tương tự, không nên throw lại
        }

        // Trả về ID của user đã được lưu
        return savedUser.getId();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateRequest req) {
        // ... (giữ nguyên)
        log.info("Attempting to update user with ID: {}", req.getId());

        UserEntity user = getUserById(req.getId());

        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setGender(req.getGender());
        user.setBirthday(req.getBirthday());
        user.setPhone(req.getPhone());

        if (!user.getEmail().equalsIgnoreCase(req.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                log.warn("Attempted to update email to an existing one: {}", req.getEmail());
                throw new IllegalArgumentException("Email '" + req.getEmail() + "' already exists");
            }
            user.setEmail(req.getEmail());
            log.info("User email updated to: {}", req.getEmail());
        }
        if (!user.getUsername().equalsIgnoreCase(req.getUsername())) {
            if (userRepository.existsByUsername(req.getUsername())) {
                log.warn("Attempted to update username to an existing one: {}", req.getUsername());
                throw new IllegalArgumentException("Username '" + req.getUsername() + "' already exists");
            }
            user.setUsername(req.getUsername());
            log.info("User username updated to: {}", req.getUsername());
        }

        log.info("Updating addresses for user ID: {}. Clearing existing addresses.", user.getId());
        user.getAddresses().clear();

        if (req.getAddresses() != null && !req.getAddresses().isEmpty()) {
            log.info("Adding {} new/updated addresses.", req.getAddresses().size());
            for (AddressRequest addressReq : req.getAddresses()) {
                AddressEntity addressEntity = new AddressEntity();
                addressEntity.setApartmentNumber(addressReq.getApartmentNumber());
                addressEntity.setFloor(addressReq.getFloor());
                addressEntity.setBuilding(addressReq.getBuilding());
                addressEntity.setStreetNumber(addressReq.getStreetNumber());
                addressEntity.setStreet(addressReq.getStreet());
                addressEntity.setCity(addressReq.getCity());
                addressEntity.setCountry(addressReq.getCountry());
                addressEntity.setAddressType(addressReq.getAddressType());
                addressEntity.setUser(user);
                user.getAddresses().add(addressEntity);
            }
        }
        userRepository.save(user);
        log.info("Successfully updated user and addresses for ID: {}", user.getId());
    }

    @Override
    @Transactional
    public void changePassword(UserPasswordRequest req) {
        // ... (giữ nguyên)
        log.info("Attempting to change password for user ID: {}", req.getId());

        UserEntity user = getUserById(req.getId());

        // Kiểm tra mật khẩu xác nhận có khớp không
        if (req.getPassword() == null || !req.getPassword().equals(req.getConfirmPassword())) {
            log.error("Password and confirm password do not match for user ID: {}", req.getId());
            throw new IllegalArgumentException("Password and confirm password do not match.");
        }

        user.setPassword(passwordEncoder.encode(req.getPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user ID: {}", user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // ... (giữ nguyên)
        log.info("Attempting soft delete for user ID: {}", id);
        UserEntity user = getUserById(id);
        user.setStatus(UserStatus.INACTIVE); // Soft delete
        userRepository.save(user);
        log.info("Soft deleted user with ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // Đảm bảo transaction
    public void verifyAccount(String token) throws ResourceNotFoundException, IllegalArgumentException {
        log.info("Attempting to verify account with token: {}", token);

        // 1. Tìm user bằng token
        UserEntity user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> {
                    log.warn("Verification failed: Invalid verification token provided - {}", token);
                    return new ResourceNotFoundException("Invalid verification token.");
                });

        // 2. Kiểm tra token hết hạn
        if (user.getTokenExpiryDate() == null || user.getTokenExpiryDate().before(Date.from(Instant.now()))) {
            log.warn("Verification failed: Token expired for user {} (Token: {})", user.getUsername(), token);
            // Cân nhắc xóa user hoặc yêu cầu gửi lại token nếu hết hạn
            throw new IllegalArgumentException("Verification token has expired.");
        }

        // 3. Kiểm tra trạng thái user
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            log.warn("Verification attempt for non-pending user {} (Status: {}, Token: {})", user.getUsername(), user.getStatus(), token);
            // Nếu đã ACTIVE, có thể chỉ trả về thông báo thành công mà không làm gì cả
            if (user.getStatus() == UserStatus.ACTIVE) {
                log.info("Account {} already active.", user.getUsername());
                 user.setVerificationToken(null);
                 user.setTokenExpiryDate(null);
                 userRepository.save(user);
                return; // Hoặc throw lỗi tùy logic mong muốn
            }
            throw new IllegalArgumentException("Account already verified or in an invalid state.");
        }

        // 4. Cập nhật trạng thái và xóa token
        log.info("Verification successful for user {}. Updating status to ACTIVE.", user.getUsername());
        user.setStatus(UserStatus.ACTIVE);
        user.setVerificationToken(null); // Xóa token sau khi xác thực thành công
        user.setTokenExpiryDate(null);  // Xóa ngày hết hạn

        // 5. Lưu thay đổi
        userRepository.save(user);
        log.info("User {} status updated to ACTIVE.", user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // Đảm bảo transaction
    public void setInitialPassword(SetInitialPasswordRequest request)
            throws ResourceNotFoundException, IllegalStateException, IllegalArgumentException {

        String identifier = request.getEmailOrToken(); // Lấy token (hoặc email nếu bạn đổi logic)
        log.info("Attempting to set initial password using identifier: {}", identifier);
        UserEntity user = userRepository.findByVerificationToken(request.getToken())
                .orElseThrow(() -> {
                    log.warn("Set initial password failed: User not found for token - {}", request.getToken());
                    // Thông báo lỗi chung chung hơn cho người dùng cuối
                    return new ResourceNotFoundException("User not found or token invalid for setting password.");
                });


        // 2. Kiểm tra trạng thái user
        // User phải ACTIVE (đã verify) nhưng chưa có password
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Set initial password failed: User {} is not ACTIVE (Status: {})", user.getUsername(), user.getStatus());
            throw new IllegalStateException("Account is not verified or not ready for password setting.");
        }

        // 3. Kiểm tra xem mật khẩu đã được đặt chưa
        if (user.getPassword() != null) {
            log.warn("Set initial password failed: Password already set for user {}", user.getUsername());
            throw new IllegalStateException("Password has already been set for this account.");
        }

        // 4. Kiểm tra mật khẩu và xác nhận mật khẩu
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("Set initial password failed: Passwords do not match for user {}", user.getUsername());
            throw new IllegalArgumentException("Passwords do not match.");
        }
        // Có thể thêm các kiểm tra độ phức tạp mật khẩu ở đây nếu muốn

        // 5. Mã hóa và đặt mật khẩu
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(encodedPassword);
        log.info("Encoded and setting password for user {}", user.getUsername());

        // 6. Xóa token (nếu bạn tìm bằng token và nó chưa bị xóa ở bước verify)
        // Nếu tìm bằng email thì bước này không cần thiết
        user.setVerificationToken(null);
        user.setTokenExpiryDate(null);
        log.info("Cleared verification token for user {} after password set.", user.getUsername());


        // 7. Lưu thay đổi
        userRepository.save(user);
        log.info("Initial password set successfully for user {}", user.getUsername());
    }


    private UserEntity getUserById(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }


}