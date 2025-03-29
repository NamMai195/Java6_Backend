package com.backend.service.impl;

import com.backend.common.UserStatus;
import com.backend.controller.request.UserCreationRequest;
import com.backend.controller.request.UserPasswordRequest;
import com.backend.controller.request.UserUpdateRequest;
import com.backend.controller.response.UserResponse;
import com.backend.exception.ResourceNotFoundException;
import com.backend.model.AddressEntity;
import com.backend.model.UserEntity;
import com.backend.repository.AddressRepository;
import com.backend.repository.UserRepository;
import com.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.InvalidIsolationLevelException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "USER-SERVICE")
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserResponse> findAll(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<UserEntity> userPage;

        if (keyword != null && !keyword.isEmpty()) {
            // Tìm kiếm theo keyword (ví dụ: tên, email, phone)
            userPage = userRepository.findByFirstNameContainingOrLastNameContainingOrEmailContainingOrPhoneContainingOrUsernameContainingAndStatus(keyword, keyword, keyword, keyword, keyword,UserStatus.ACTIVE, pageable);
        }else {
            // Lấy tất cả người dùng có trạng thái ACTIVE (thay đổi ở đây)
            userPage = userRepository.findByStatus(UserStatus.ACTIVE, pageable); // thay đổi
        }

        return userPage.getContent().stream()
                .map(userEntity -> UserResponse.builder()
                        .id(userEntity.getId())
                        .firstName(userEntity.getFirstName())
                        .lastName(userEntity.getLastName())
                        .gender(userEntity.getGender())
                        .birthday(userEntity.getBirthday())
                        .username(userEntity.getUsername())
                        .phone(userEntity.getPhone())
                        .email(userEntity.getEmail())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse findByUserName(String userName) {
        return null;
    }

    @Override
    public UserResponse findById(Long id) {
        log.info("Find user by id: {}", id);

        UserEntity userEntity = getUserById(id);

        return UserResponse.builder()
                .id(id)
                .firstName(userEntity.getFirstName())
                .lastName(userEntity.getLastName())
                .gender(userEntity.getGender())
                .birthday(userEntity.getBirthday())
                .username(userEntity.getUsername())
                .phone(userEntity.getPhone())
                .email(userEntity.getEmail())
                .build();
    }

    @Override
    public UserResponse findByEmail(String email) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long save(UserCreationRequest req) {
        log.info("Saving user {}", req);

        UserEntity userByEmail= userRepository.findByEmail(req.getEmail());

        if (userByEmail !=null){
            throw new InvalidIsolationLevelException("Email already exists");
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
        user.setStatus(UserStatus.NONE);
        userRepository.save(user);
        log.info("Saved user {}", user);
        if(user.getId() != null) {
            log.info("User id is {}", user.getId());
            List<AddressEntity> address=new ArrayList<>();
            req.getAddresses().forEach(addressEntity -> {
                AddressEntity AddressEntity = new AddressEntity();
                AddressEntity.setApartmentNumber(addressEntity.getApartmentNumber());
                AddressEntity.setFloor(addressEntity.getFloor());
                AddressEntity.setBuilding(addressEntity.getBuilding());
                AddressEntity.setStreetNumber(addressEntity.getStreetNumber());
                AddressEntity.setStreet(addressEntity.getStreet());
                AddressEntity.setCity(addressEntity.getCity());
                AddressEntity.setCountry(addressEntity.getCountry());
                AddressEntity.setAddressType(addressEntity.getAddressType());
                AddressEntity.setUserid(user.getId());
                address.add(AddressEntity);
            });
            addressRepository.saveAll(address);
            log.info("Saving address {}", address);
        }
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateRequest req) {
        log.info("Updating user: {}", req);

        // Get user by id
        UserEntity user = getUserById(req.getId());
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setGender(req.getGender());
        user.setBirthday(req.getBirthday());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setUsername(req.getUsername());

        userRepository.save(user);
        log.info("Updated user: {}", user);

        // save address
        List<AddressEntity> addresses = new ArrayList<>();

        req.getAddresses().forEach(address -> {
            AddressEntity addressEntity = addressRepository.findByUseridAndAddressType(user.getId(), address.getAddressType());
            if (addressEntity == null) {
                addressEntity = new AddressEntity();
            }
            addressEntity.setApartmentNumber(address.getApartmentNumber());
            addressEntity.setFloor(address.getFloor());
            addressEntity.setBuilding(address.getBuilding());
            addressEntity.setStreetNumber(address.getStreetNumber());
            addressEntity.setStreet(address.getStreet());
            addressEntity.setCity(address.getCity());
            addressEntity.setCountry(address.getCountry());
            addressEntity.setAddressType(address.getAddressType());
            addressEntity.setUserid(user.getId());

            addresses.add(addressEntity);
        });

        // save addresses
        addressRepository.saveAll(addresses);
        log.info("Updated addresses: {}", addresses);
    }

    @Override
    public void ChangePassword(UserPasswordRequest req) {
        log.info("Changing password for user: {}", req);

        // Get user by id
        UserEntity user = getUserById(req.getId());
        if (req.getPassword().equals(req.getConfirmPassword())) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        userRepository.save(user);
        log.info("Changed password for user: {}", user);
    }

    @Override
    public void delete(Long id) {
        log.info("Deleting user: {}", id);
        //get user
        UserEntity user = getUserById(id);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    private UserEntity getUserById(Long id){
        return userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("User not found"));
    }
}
