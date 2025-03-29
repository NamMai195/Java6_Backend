package com.backend.controller.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CategoryBasicResponse {
    private Long id;
    private String name;
}