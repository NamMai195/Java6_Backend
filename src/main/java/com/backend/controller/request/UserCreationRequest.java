package com.backend.controller.request;


import com.backend.common.Gender;
import com.backend.common.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.ToString;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Getter
@ToString
@Validated
public class UserCreationRequest implements Serializable {
    @NotBlank(message = "First Name is required")
    private String firstName;
    @NotBlank(message = "First Name is required")
    private String lastName;
    private Gender gender;
    private Date birthday;
    private String username;
    @Email(message = "Email Invalid")
    private String email;
    private String phone;
    private UserType type;
    private List<AddressRequest> addresses; // home,office
}
