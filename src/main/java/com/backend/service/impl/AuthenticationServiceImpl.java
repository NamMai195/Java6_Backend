package com.backend.service.impl;

import com.backend.common.TokenType;
import com.backend.controller.request.SignInRequest;
import com.backend.controller.response.TokenResponse;
import com.backend.exception.InvalidDataException;
import com.backend.model.UserEntity;
import com.backend.repository.UserRepository;
import com.backend.service.AuthenticationService;
import com.backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.backend.common.TokenType.REFRESH_TOKEN;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "AUTHENTICATION-SERVICE")
public class AuthenticationServiceImpl implements AuthenticationService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;

    @Override
    public TokenResponse getAccessToken(SignInRequest request) {
        log.info("Attempting to authenticate user: {}", request.getUsername());

        Authentication authenticate;
        try {
            // Xác thực bằng AuthenticationManager
            authenticate = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            // Lưu vào SecurityContext nếu thành công
            SecurityContextHolder.getContext().setAuthentication(authenticate);
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for user {}: Invalid credentials", request.getUsername());
            throw new AccessDeniedException("Invalid username or password");
        } catch (DisabledException e) {
            log.error("Authentication failed for user {}: Account disabled", request.getUsername());
            throw new AccessDeniedException("User account is disabled");
        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", request.getUsername(), e.getMessage());
            throw new AccessDeniedException("Authentication failed: " + e.getMessage());
        }

        // Lấy thông tin user từ repository
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found after authentication: " + request.getUsername()));

        // Tạo access token và refresh token
        log.info("Generating tokens for user {}", user.getUsername());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getAuthorities());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getAuthorities());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public TokenResponse getRefreshToken(String refreshToken) {
        log.info("Attempting to refresh token");

        if (!StringUtils.hasLength(refreshToken)) {
            throw new InvalidDataException("Refresh token must not be blank");
        }

        try {
            // Trích xuất username và xác thực refresh token
            String username = jwtService.extractUsername(refreshToken, REFRESH_TOKEN);
            log.info("Extracted username {} from refresh token", username);

            // Lấy thông tin user
            UserEntity user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User associated with refresh token not found: " + username));

            // (Tùy chọn) Kiểm tra user có bị khóa hay không
            if (!user.isEnabled()) {
                log.error("User {} associated with refresh token is not enabled.", username);
                throw new AccessDeniedException("User account is not active");
            }

            // Tạo access token mới
            log.info("Generating new access token for user {}", username);
            String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getAuthorities());

            // Trả về access token mới và refresh token cũ
            return TokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (AccessDeniedException e) {
            // Lỗi từ jwtService (token hết hạn/không hợp lệ)
            log.error("Refresh token validation failed: {}", e.getMessage());
            throw e; // Ném lại lỗi AccessDeniedException
        } catch (UsernameNotFoundException | InvalidDataException e) {
            log.error("Error during refresh token processing: {}", e.getMessage());
            throw e; // Ném lại lỗi cụ thể
        } catch (Exception e) {
            log.error("Unexpected error during token refresh: {}", e.getMessage(), e);
            throw new AccessDeniedException("Could not refresh token due to an unexpected error.");
        }
    }
}