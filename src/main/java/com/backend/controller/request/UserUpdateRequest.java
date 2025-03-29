package com.backend.controller.request;

import com.backend.common.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Getter
@ToString
public class UserUpdateRequest implements Serializable {
    @NotNull(message = "Id is required")
    @Min(value = 1,message = "UserId must be greater than 0 ")
    private Long id;
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
    private List<AddressRequest> addresses;
}
