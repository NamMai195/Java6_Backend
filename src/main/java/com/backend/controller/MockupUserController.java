package com.backend.controller;

import com.backend.controller.request.UserCreationRequest;
import com.backend.controller.request.UserPasswordRequest;
import com.backend.controller.request.UserUpdateRequest;
import com.backend.controller.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/mockup/user")
@Tag(name = "MockupUserController")
public class MockupUserController {

    @Operation(summary = "Get User List", description = "API get user from db")
    @GetMapping("/list")
    public Map<String, Object> getList(@RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
//        UserResponse userResponse1 = new UserResponse();
//        userResponse1.setId("1");
//        userResponse1.setName("John Doe");
//        userResponse1.setEmail("john.doe@example.com");
//        userResponse1.setPhone("123-456-7890");
//        userResponse1.setAddress("123 Main St, Anytown");
//        userResponse1.setGender("Male");

//        UserResponse userResponse2 = new UserResponse();
//        userResponse2.setId("2");
//        userResponse2.setName("Jane Smith");
//        userResponse2.setEmail("jane.smith@example.com");
//        userResponse2.setPhone("987-654-3210");
//        userResponse2.setAddress("456 Oak Ave, Somecity");
//        userResponse2.setGender("Female");
//        List<UserResponse> userList = List.of(
//                userResponse1, userResponse2
//        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.OK.value());
        result.put("message", "User List");
        result.put("data", "userList");
        return result;
    }

    @Operation(summary = "Get User Detail", description = "API get user detail from db")
    @GetMapping("/{userId}") // Corrected Path Variable name
    public Map<String, Object> getUserDetail(@PathVariable String userId) { // Changed PathVariable type to String

//        UserResponse userResponse2 = new UserResponse();
//        userResponse2.setId(userId); // Use userId from path
//        userResponse2.setName("Jane Smith");
//        userResponse2.setEmail("jane.smith@example.com");
//        userResponse2.setPhone("987-654-3210");
//        userResponse2.setAddress("456 Oak Ave, Somecity");
//        userResponse2.setGender("Female");
//        List<UserResponse> userList = List.of(
//                userResponse2
//        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.OK.value());
        result.put("message", "User Detail");
        result.put("data", "userList");
        return result;
    }

    @Operation(summary = "Create User", description = "API to create a new user")
    @PostMapping("/") // Use PostMapping for creating resources
    public Map<String, Object> createUser(@RequestBody UserCreationRequest userRequest) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.CREATED.value()); // Use 201 Created for successful creation
        result.put("message", "User Created");
        result.put("data", 3);
        return result;
    }

    @Operation(summary = "Update User", description = "API to update an existing user")
    @PutMapping("/update")
    public Map<String, Object> updateUser(UserUpdateRequest request) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.ACCEPTED.value());
        result.put("message", "User Updated");
        result.put("data", "");
        return result;
    }

    @Operation(summary = "Change Password", description = "API to change user password")
    @PatchMapping("/change-password") // Use PostMapping for changing password
    public Map<String, Object> changePass(@RequestBody UserPasswordRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.OK.value());
        result.put("message", "Password Changed");
        result.put("data", null); // Or a success message.
        return result;
    }

    @Operation(summary = "Delete User", description = "API to delete a user")
    @DeleteMapping("/delete/{userId}") // Use DeleteMapping for deleting resources
    public Map<String, Object> deleteUser(@PathVariable Long userId) {


        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", HttpStatus.NO_CONTENT.value());
        result.put("message", "User Deleted");
        result.put("data", null); // Or you could return an empty list
        return result;
    }
}