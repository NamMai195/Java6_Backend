package com.backend.service;

import com.backend.controller.request.UserCreationRequest;
import com.backend.controller.request.UserPasswordRequest;
import com.backend.controller.request.UserUpdateRequest;
import com.backend.controller.response.UserResponse;

import java.util.List;

public interface UserService {

    List<UserResponse> findAll(String keywork, int page, int size);
    UserResponse findById(Long id);
    UserResponse findByUserName(String userName);
    UserResponse findByEmail(String email);
    long save(UserCreationRequest req);
    void update(UserUpdateRequest req);
    void ChangePassword(UserPasswordRequest req);
    void delete(Long id);
}
