package com.backend.controller.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPageReponse extends PageResponeAbstract{
    private List<UserResponse> users;
}
