package com.backend.service;

import com.backend.controller.request.SignInRequest;
import com.backend.controller.response.TokenResponse;

public interface AuthenticationService {

    TokenResponse getAccessToken(SignInRequest request);

    TokenResponse getRefreshToken(String request);
}
