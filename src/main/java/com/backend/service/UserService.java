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

    List<UserResponse> findAll(String keyword, Pageable pageable);

    UserResponse findById(Long id);
    UserResponse findByUserName(String userName);
    UserResponse findByEmail(String email);
    long save(UserCreationRequest req);
    void update(UserUpdateRequest req);

    void changePassword(UserPasswordRequest req);

    void delete(Long id);

    void verifyAccount(String token) throws ResourceNotFoundException, IllegalArgumentException;
    void setInitialPassword(SetInitialPasswordRequest request) throws ResourceNotFoundException, IllegalStateException, IllegalArgumentException;
    /**
     * Processes the user after successful login via OAuth2 (e.g., Google).
     * Finds the user by email; if not found, creates a new user.
     * @param email Email obtained from Google
     * @param firstName First name obtained from Google
     * @param lastName Last name obtained from Google
     * @param imageUrl Profile picture URL from Google (can be null)
     * @return The UserEntity that was found or newly saved in the database
     */
    UserEntity processOAuthPostLogin(String email, String firstName, String lastName, String imageUrl);
}
