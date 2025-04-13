package com.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter // Added Setter for request body binding if needed
public class SetInitialPasswordRequest implements Serializable {

    // Use token to identify the user for setting the initial password
    @NotBlank(message = "Token cannot be blank")
    private String token;

    // Email alternative commented out, assuming token-based flow
    // @NotBlank(message = "Email cannot be blank")
    // @jakarta.validation.constraints.Email(message = "Invalid email format")
    // private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long") // Optional: Add length constraint
    private String password;

    @NotBlank(message = "Confirm Password is required")
    private String confirmPassword;

    // Helper method to get the identifier (token in this case)
    public String getEmailOrToken() {
        // if (email != null) return email; // Keep commented if using token
        return token; // Return token as the identifier
    }
}
