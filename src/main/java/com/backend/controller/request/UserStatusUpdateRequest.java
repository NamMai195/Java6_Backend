package com.backend.controller.request;

import com.backend.common.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull(message = "Status cannot be null")
        UserStatus status,
        String reason
) {}