package com.backend.controller;

import com.backend.controller.request.UserCreationRequest;
import com.backend.controller.request.UserPasswordRequest;
import com.backend.controller.request.UserUpdateRequest;

import com.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;

import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "UserController")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get User List", description = "API get user from db")
    @GetMapping("/list")

    public Map<String, Object> getList(@RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        log.info("Get User List");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.OK.value());
        result.put("message", "User List");
        result.put("data", userService.findAll(keyword, page, size));
        return result;
    }

    @Operation(summary = "Get User Detail", description = "API get user detail from db")
    @GetMapping("/{userId}") // Corrected Path Variable name
    public Map<String, Object> getUserDetail(@PathVariable @Min(value = 1,message = "UserId must be greater than 0 ") Long userId) { // Changed PathVariable type to String
        log.info("Get User Detail for user: {}", userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.OK.value());
        result.put("message", "User Detail");
        result.put("data", userService.findById(userId));
        return result;
    }

    @Operation(summary = "Create User", description = "API to create a new user")
    @PostMapping("/create") // Use PostMapping for creating resources
    public ResponseEntity<Object> createUser(@RequestBody  @Valid UserCreationRequest userRequest) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.CREATED.value()); // Use 201 Created for successful creation
        result.put("message", "User Created");
        result.put("data", userService.save(userRequest));
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }
//done
    @Operation(summary = "Update User", description = "API to update an existing user")
    @PutMapping("/update")
    public Map<String, Object> updateUser(@RequestBody @Valid UserUpdateRequest request) {
        log.info("Updating user {}", request);
        userService.update(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.ACCEPTED.value());
        result.put("message", "User updated successfully");
        result.put("data", "");

        return result;
    }
//done
    @Operation(summary = "Change Password", description = "API to change user password")
    @PatchMapping("/change-password") // Use PostMapping for changing password
    public Map<String, Object> changePass(@RequestBody @Valid UserPasswordRequest request) {
        log.info("Changing password for user {}", request);
        userService.ChangePassword(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.OK.value());
        result.put("message", "Password Changed");
        result.put("data", null); // Or a success message.
        return result;
    }
//done
    @Operation(summary = "Delete User", description = "API to delete a user")
    @DeleteMapping("/delete/{userId}") // Use DeleteMapping for deleting resources
    public Map<String, Object> deleteUser(@PathVariable @Valid @Min(value = 1,message = "UserId must be greater than 0 ") Long userId) {
        log.info("Deleting user: {}",userId);
        userService.delete(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.NO_CONTENT.value());
        result.put("message", "User Deleted");
        result.put("data", null); // Or you could return an empty list
        return result;
    }
}