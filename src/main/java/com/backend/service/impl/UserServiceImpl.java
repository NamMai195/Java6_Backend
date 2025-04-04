// src/main/java/com/backend/service/impl/UserServiceImpl.java
package com.backend.service.impl;

import com.backend.common.UserStatus;
import com.backend.controller.request.*;
import com.backend.controller.response.AddressResponse; // Phải import AddressResponse
import com.backend.controller.response.UserResponse;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.AddressEntity;
import com.backend.model.UserEntity;
import com.backend.repository.AddressRepository;
import com.backend.repository.UserRepository;
import com.backend.service.BrevoEmailService;
import com.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate; // Import Hibernate
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import sendinblue.ApiException;

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
    private final BrevoEmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${app.email.verification-template-id:1}")
    private Long verificationTemplateId;

    @Value("${app.email.defaults.service-name:My Application}")
    private String defaultServiceName;

    @Value("${app.email.defaults.company-name:My Company Inc.}")
    private String defaultCompanyName;

    // --- SỬA LẠI HÀM MAP NÀY ---
    private UserResponse mapToUserResponse(UserEntity userEntity) {
        UserResponse.UserResponseBuilder builder = UserResponse.builder()
                .id(userEntity.getId())
                .firstName(userEntity.getFirstName())
                .lastName(userEntity.getLastName())
                .gender(userEntity.getGender())
                .birthday(userEntity.getBirthday())
                .username(userEntity.getUsername())
                .phone(userEntity.getPhone())
                .email(userEntity.getEmail())
                .type(userEntity.getType())
                .status(userEntity.getStatus());

        // Thêm logic map addresses
        if (Hibernate.isInitialized(userEntity.getAddresses()) && userEntity.getAddresses() != null) {
            builder.addresses(userEntity.getAddresses().stream()
                    .map(this::mapToAddressResponse) // Gọi hàm map địa chỉ
                    .collect(Collectors.toList()));
        } else {
            builder.addresses(Collections.emptyList()); // Trả về list rỗng nếu chưa initialize
            if (!Hibernate.isInitialized(userEntity.getAddresses())) {
                log.warn("Addresses collection was not initialized when mapping UserResponse for user {}", userEntity.getId());
            }
        }

        return builder.build();
    }

    // Hàm map địa chỉ (Đảm bảo tồn tại và chính xác)
    private AddressResponse mapToAddressResponse(AddressEntity addressEntity) {
        if (addressEntity == null) return null;
        return AddressResponse.builder()
                .id(addressEntity.getId())
                .apartmentNumber(addressEntity.getApartmentNumber())
                .floor(addressEntity.getFloor())
                .building(addressEntity.getBuilding())
                .streetNumber(addressEntity.getStreetNumber())
                .street(addressEntity.getStreet())
                .ward(addressEntity.getWard())
                .district(addressEntity.getDistrict())
                .city(addressEntity.getCity())
                .country(addressEntity.getCountry())
                .addressType(addressEntity.getAddressType())
                // Map các trường code nếu có và cần thiết
                // .provinceCode(addressEntity.getProvinceCode())
                // .districtCode(addressEntity.getDistrictCode())
                // .wardCode(addressEntity.getWardCode())
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll(String keyword, Pageable pageable) {
        Page<UserEntity> userPage;
        if (StringUtils.hasText(keyword)) {
            log.info("Finding users with keyword '{}', page {}, size {}", keyword, pageable.getPageNumber(), pageable.getPageSize());
            userPage = userRepository.searchUsersByKeywordAndStatus(keyword, UserStatus.ACTIVE, pageable);
        } else {
            log.info("Finding all active users, page {}, size {}", pageable.getPageNumber(), pageable.getPageSize());
            userPage = userRepository.findByStatus(UserStatus.ACTIVE, pageable);
        }

        // Initialize addresses cho từng user trong list trước khi map
        List<UserEntity> userEntities = userPage.getContent();
        userEntities.forEach(user -> {
            try {
                Hibernate.initialize(user.getAddresses());
            } catch (Exception e) {
                log.error("Error initializing addresses for user {}: {}", user.getId(), e.getMessage());
            }
        });

        return userEntities.stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByUserName(String userName) {
        log.info("Finding user by username: {}", userName);
        UserEntity user = userRepository.findByUsername(userName)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + userName));
        try {
            Hibernate.initialize(user.getAddresses());
            log.debug("Successfully initialized addresses for user {}", userName);
        } catch (Exception e) {
            log.error("Error initializing addresses for user {}: {}", userName, e.getMessage(), e);
        }
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        log.info("Finding user by id: {}", id);
        UserEntity userEntity = getUserById(id);
        try {
            Hibernate.initialize(userEntity.getAddresses());
            log.debug("Successfully initialized addresses for user {}", id);
        } catch (Exception e) {
            log.error("Error initializing addresses for user {}: {}", id, e.getMessage(), e);
        }
        return mapToUserResponse(userEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        log.info("Finding user by email: {}", email);
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        try {
            Hibernate.initialize(user.getAddresses());
            log.debug("Successfully initialized addresses for user with email {}", email);
        } catch (Exception e) {
            log.error("Error initializing addresses for user with email {}: {}", email, e.getMessage(), e);
        }
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long save(UserCreationRequest req) {
        log.info("Registering new user: {}", req.getUsername());

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
        user.setPassword(null);
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setTokenExpiryDate(Date.from(Instant.now().plus(Duration.ofHours(24))));
        log.info("Generated verification token for user {}: {}", user.getUsername(), token);

        Set<AddressEntity> addresses = new HashSet<>();
        if (req.getAddresses() != null && !req.getAddresses().isEmpty()) {
            log.info("Processing {} addresses for new user {}", req.getAddresses().size(), req.getUsername());
            for (AddressRequest addressReq : req.getAddresses()) {
                AddressEntity addressEntity = new AddressEntity();
                addressEntity.setApartmentNumber(addressReq.getApartmentNumber());
                addressEntity.setFloor(addressReq.getFloor());
                addressEntity.setBuilding(addressReq.getBuilding());
                addressEntity.setStreetNumber(addressReq.getStreetNumber());
                addressEntity.setStreet(addressReq.getStreet());
                addressEntity.setWard(addressReq.getWard());
                addressEntity.setDistrict(addressReq.getDistrict());
                addressEntity.setCity(addressReq.getCity());
                addressEntity.setCountry(addressReq.getCountry());
                addressEntity.setAddressType(addressReq.getAddressType());
                // Map code nếu có
                // addressEntity.setProvinceCode(addressReq.getProvinceCode());
                // addressEntity.setDistrictCode(addressReq.getDistrictCode());
                // addressEntity.setWardCode(addressReq.getWardCode());
                addressEntity.setUser(user);
                addresses.add(addressEntity);
            }
        }
        user.setAddresses(addresses);

        UserEntity savedUser = userRepository.save(user);
        log.info("Saved user pending verification with ID: {}", savedUser.getId());

        try {
            String verificationLink = "http://localhost:5173/set-initial-password?token=" + savedUser.getVerificationToken();
            Map<String, Object> emailParams = new HashMap<>();
            emailParams.put("user_name", savedUser.getFirstName());
            emailParams.put("verification_link", verificationLink);
            emailParams.put("service_name", defaultServiceName);
            emailParams.put("company_name", defaultCompanyName);

            log.info("Attempting to send verification email to {} using template ID {}", savedUser.getEmail(), verificationTemplateId);
            emailService.sendEmailWithTemplate(
                    savedUser.getEmail(),
                    verificationTemplateId,
                    emailParams
            );
            log.info("Verification email sent successfully to {}", savedUser.getEmail());
        } catch (ApiException e) {
            log.error("Brevo API Exception while sending verification email to {}: Status Code: {}, Response Body: {}",
                    savedUser.getEmail(), e.getCode(), e.getResponseBody(), e);
        } catch (Exception e) {
            log.error("Unexpected error while sending verification email to {}: {}",
                    savedUser.getEmail(), e.getMessage(), e);
        }
        return savedUser.getId();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateRequest req) {
        if (req.getId() == null) {
            log.error("Update request received without user ID.");
            throw new IllegalArgumentException("User ID must be provided in the update request.");
        }
        log.info("Attempting to update user profile with ID: {}", req.getId());

        UserEntity user = getUserById(req.getId());

        if (StringUtils.hasText(req.getFirstName())) {
            user.setFirstName(req.getFirstName());
        }
        if (StringUtils.hasText(req.getLastName())) {
            user.setLastName(req.getLastName());
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender());
        }
        user.setBirthday(req.getBirthday());
        if(req.getBirthday() != null) log.debug("Updating birthday for user {}", req.getId());
        else log.debug("Setting birthday to null for user {}", req.getId());

        if (req.getPhone() != null) {
            user.setPhone(StringUtils.hasText(req.getPhone()) ? req.getPhone() : null);
        }

        log.info("Updating addresses for user ID: {}. Clearing existing addresses first.", user.getId());
        user.getAddresses().clear();

        if (req.getAddresses() != null && !req.getAddresses().isEmpty()) {
            log.info("Adding {} new/updated addresses.", req.getAddresses().size());
            Set<AddressEntity> newAddresses = new HashSet<>();
            for (AddressRequest addressReq : req.getAddresses()) {
                AddressEntity addressEntity = new AddressEntity();
                addressEntity.setApartmentNumber(addressReq.getApartmentNumber());
                addressEntity.setFloor(addressReq.getFloor());
                addressEntity.setBuilding(addressReq.getBuilding());
                addressEntity.setStreetNumber(addressReq.getStreetNumber());
                addressEntity.setStreet(addressReq.getStreet());
                addressEntity.setCountry(addressReq.getCountry());
                addressEntity.setAddressType(addressReq.getAddressType());
                addressEntity.setCity(addressReq.getCity());
                addressEntity.setDistrict(addressReq.getDistrict());
                addressEntity.setWard(addressReq.getWard());
                // Gán cả code nếu bạn đã thêm vào Entity và Request
                // addressEntity.setProvinceCode(addressReq.getProvinceCode());
                // addressEntity.setDistrictCode(addressReq.getDistrictCode());
                // addressEntity.setWardCode(addressReq.getWardCode());
                addressEntity.setUser(user);
                newAddresses.add(addressEntity);
            }
            user.getAddresses().addAll(newAddresses);
        } else {
            log.info("No addresses provided in the update request for user {}.", req.getId());
        }

        userRepository.save(user);
        log.info("Successfully updated user profile (excluding email/username) and addresses for ID: {}", user.getId());
    }

    @Override
    @Transactional
    public void changePassword(UserPasswordRequest req) {
        log.info("Attempting to change password for user ID: {}", req.getId());
        UserEntity user = getUserById(req.getId());

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
        log.info("Attempting soft delete for user ID: {}", id);
        UserEntity user = getUserById(id);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("Soft deleted user with ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyAccount(String token) throws ResourceNotFoundException, IllegalArgumentException {
        log.info("Attempting to verify account with token: {}", token);
        UserEntity user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> {
                    log.warn("Verification failed: Invalid verification token provided - {}", token);
                    return new ResourceNotFoundException("Invalid verification token.");
                });

        if (user.getTokenExpiryDate() == null || user.getTokenExpiryDate().before(Date.from(Instant.now()))) {
            log.warn("Verification failed: Token expired for user {} (Token: {})", user.getUsername(), token);
            throw new IllegalArgumentException("Verification token has expired.");
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            log.info("Verification successful for user {}. Updating status to ACTIVE.", user.getUsername());
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
            log.info("User {} status updated to ACTIVE.", user.getUsername());

        } else if (user.getStatus() == UserStatus.ACTIVE) {
            log.info("Account {} already active. No action needed.", user.getUsername());
            return;
        } else {
            log.warn("Verification attempt for user {} with invalid status (Status: {}, Token: {})", user.getUsername(), user.getStatus(), token);
            throw new IllegalArgumentException("Account is not in a state that allows verification.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setInitialPassword(SetInitialPasswordRequest request)
            throws ResourceNotFoundException, IllegalStateException, IllegalArgumentException {

        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("Token is required.");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }

        log.info("Attempting to set initial password using token: {}", request.getToken());
        UserEntity user = userRepository.findByVerificationToken(request.getToken())
                .orElseThrow(() -> {
                    log.warn("Set initial password failed: User not found or token invalid - {}", request.getToken());
                    return new ResourceNotFoundException("User not found or token invalid for setting password.");
                });

        if (user.getTokenExpiryDate() == null || user.getTokenExpiryDate().before(Date.from(Instant.now()))) {
            log.warn("Set initial password failed: Token expired for user {} (Token: {})", user.getUsername(), request.getToken());
            throw new IllegalArgumentException("Token for setting password has expired. Please request verification again.");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Set initial password failed: User {} is not ACTIVE (Status: {})", user.getUsername(), user.getStatus());
            throw new IllegalStateException("Account is not verified or not ready for password setting.");
        }

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            log.warn("Set initial password failed: Password already set for user {}", user.getUsername());
            throw new IllegalStateException("Password has already been set for this account.");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("Set initial password failed: Passwords do not match for user {}", user.getUsername());
            throw new IllegalArgumentException("Passwords do not match.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(encodedPassword);
        log.info("Encoded and setting password for user {}", user.getUsername());

        user.setVerificationToken(null);
        user.setTokenExpiryDate(null);
        log.info("Cleared verification token for user {} after password set.", user.getUsername());

        userRepository.save(user);
        log.info("Initial password set successfully for user {}", user.getUsername());
    }

    private UserEntity getUserById(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}