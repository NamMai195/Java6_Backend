package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserBasicResponse {
    private Long userId;
    private String username; // Hoặc firstName + lastName tùy bạn muốn hiển thị gì
}