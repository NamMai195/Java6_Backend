package com.backend.service.impl;

// ... (Giữ nguyên các import) ...
import com.backend.common.TokenType;
import com.backend.common.UserStatus;
import com.backend.common.UserType;
import com.backend.controller.request.SignInRequest;
import com.backend.controller.response.TokenResponse;
import com.backend.exception.InvalidDataException;
import com.backend.model.UserEntity;
import com.backend.repository.UserRepository;
import com.backend.service.JwtService;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AuthenticationServiceImplTestNG { // Giữ tên class của bạn nếu muốn

    // --- Mocks ---
    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private Authentication successfulAuthentication;

    // --- Class Under Test ---
    @InjectMocks private AuthenticationServiceImpl authenticationService;

    // --- Test Data ---
    private SignInRequest signInRequest;
    private UserEntity userEntity;
    private String dummyAccessToken;
    private String dummyRefreshToken;
    private UsernamePasswordAuthenticationToken expectedAuthToken;

    // === QUAY LẠI DÙNG BeforeMethod với openMocks ===
    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this); // Khởi tạo lại bằng cách này
        SecurityContextHolder.clearContext();

        // ... (Phần setup data giữ nguyên như trước) ...
        signInRequest = new SignInRequest();
        signInRequest.setUsername("testuser");
        signInRequest.setPassword("password123");

        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("testuser");
        userEntity.setPassword("hashedPassword");
        userEntity.setType(UserType.USER);
        userEntity.setStatus(UserStatus.ACTIVE);

        dummyAccessToken = "dummy.access.token.xyz";
        dummyRefreshToken = "dummy.refresh.token.abc";

        expectedAuthToken = new UsernamePasswordAuthenticationToken(
                signInRequest.getUsername(), signInRequest.getPassword());
    }
    // ==========================================

    // === GIỮ LẠI AfterMethod reset ===
    @AfterMethod
    public void tearDown() {
        reset(jwtService, userRepository, authenticationManager, successfulAuthentication);
    }
    // ==============================


    // --- CÁC PHƯƠNG THỨC @Test GIỮ NGUYÊN ---
    // (Giữ nguyên các phiên bản đã sửa lỗi matcher và bỏ expectedExceptionsMessageRegExp)

    @Test(description = "getAccessToken: Thành công - Trả về token khi đăng nhập đúng")
    public void getAccessToken_Success() {
        // Arrange
        when(authenticationManager.authenticate(eq(expectedAuthToken)))
                .thenReturn(successfulAuthentication);
        when(userRepository.findByUsername(eq(signInRequest.getUsername()))).thenReturn(Optional.of(userEntity));
        Collection<? extends GrantedAuthority> expectedAuthorities = userEntity.getAuthorities();
        when(jwtService.generateAccessToken(userEntity.getId(), userEntity.getUsername(), expectedAuthorities))
                .thenReturn(dummyAccessToken);
        when(jwtService.generateRefreshToken(userEntity.getId(), userEntity.getUsername(), expectedAuthorities))
                .thenReturn(dummyRefreshToken);
        // Act
        TokenResponse actualResponse = authenticationService.getAccessToken(signInRequest);
        // Assert
        Assert.assertNotNull(actualResponse);
        Assert.assertEquals(actualResponse.getAccessToken(), dummyAccessToken);
        Assert.assertEquals(actualResponse.getRefreshToken(), dummyRefreshToken);
        // Verify
        verify(authenticationManager).authenticate(eq(expectedAuthToken));
        verify(userRepository).findByUsername(eq(signInRequest.getUsername()));
        verify(jwtService).generateAccessToken(userEntity.getId(), userEntity.getUsername(), expectedAuthorities);
        verify(jwtService).generateRefreshToken(userEntity.getId(), userEntity.getUsername(), expectedAuthorities);
    }

    @Test(description = "getAccessToken: Thất bại - Sai thông tin đăng nhập",
            expectedExceptions = AccessDeniedException.class)
    public void getAccessToken_BadCredentials() {
        // Arrange
        when(authenticationManager.authenticate(eq(expectedAuthToken)))
                .thenThrow(new BadCredentialsException("Bad creds"));
        // Act
        authenticationService.getAccessToken(signInRequest);
        // Assert: Implicit
    }

    @Test(description = "getAccessToken: Thất bại - Tài khoản bị khóa (DisabledException từ AuthManager)",
            expectedExceptions = AccessDeniedException.class)
    public void getAccessToken_AccountDisabledViaAuthManager() {
        // Arrange
        when(authenticationManager.authenticate(eq(expectedAuthToken)))
                .thenThrow(new DisabledException("Disabled"));
        // Act
        authenticationService.getAccessToken(signInRequest);
        // Assert: Implicit
    }

    @Test(description = "getAccessToken: Thất bại - Lỗi xác thực khác",
            expectedExceptions = AccessDeniedException.class)
    public void getAccessToken_OtherAuthError() {
        // Arrange
        when(authenticationManager.authenticate(eq(expectedAuthToken)))
                .thenThrow(new RuntimeException("Some other auth issue"));
        // Act
        authenticationService.getAccessToken(signInRequest);
        // Assert: Implicit
    }

    @Test(description = "getAccessToken: Thất bại - User không tìm thấy SAU KHI xác thực thành công",
            expectedExceptions = UsernameNotFoundException.class)
    public void getAccessToken_UserNotFoundAfterAuthSuccess() {
        // Arrange
        when(authenticationManager.authenticate(eq(expectedAuthToken)))
                .thenReturn(successfulAuthentication);
        when(userRepository.findByUsername(eq(signInRequest.getUsername()))).thenReturn(Optional.empty());
        // Act
        authenticationService.getAccessToken(signInRequest);
        // Assert: Implicit
    }

    // ... (getRefreshToken tests giữ nguyên cấu trúc như phiên bản trước) ...
    @Test(description = "getRefreshToken: Thành công - Trả về access token mới")
    public void getRefreshToken_Success() {
        // Arrange
        String validRefreshToken = "valid.refresh.token.123";
        String newAccessToken = "new.access.token.456";
        userEntity.setStatus(UserStatus.ACTIVE);
        when(jwtService.extractUsername(eq(validRefreshToken), eq(TokenType.REFRESH_TOKEN)))
                .thenReturn(userEntity.getUsername());
        when(userRepository.findByUsername(eq(userEntity.getUsername())))
                .thenReturn(Optional.of(userEntity));
        Collection<? extends GrantedAuthority> expectedAuthorities = userEntity.getAuthorities();
        when(jwtService.generateAccessToken(userEntity.getId(), userEntity.getUsername(), expectedAuthorities))
                .thenReturn(newAccessToken);
        // Act
        TokenResponse actualResponse = authenticationService.getRefreshToken(validRefreshToken);
        // Assert
        Assert.assertNotNull(actualResponse);
        Assert.assertEquals(actualResponse.getAccessToken(), newAccessToken);
        Assert.assertEquals(actualResponse.getRefreshToken(), validRefreshToken);
        // Verify
        verify(jwtService).extractUsername(eq(validRefreshToken), eq(TokenType.REFRESH_TOKEN));
        verify(userRepository).findByUsername(eq(userEntity.getUsername()));
        verify(jwtService).generateAccessToken(userEntity.getId(), userEntity.getUsername(), expectedAuthorities);
        verify(jwtService, never()).generateRefreshToken(anyLong(), anyString(), any());
    }

    @Test(description = "getRefreshToken: Thất bại - Refresh token rỗng",
            expectedExceptions = InvalidDataException.class)
    public void getRefreshToken_BlankToken() {
        authenticationService.getRefreshToken("");
    }

    @Test(description = "getRefreshToken: Thất bại - Refresh token null",
            expectedExceptions = InvalidDataException.class)
    public void getRefreshToken_NullToken() {
        authenticationService.getRefreshToken(null);
    }

    @Test(description = "getRefreshToken: Thất bại - Token không hợp lệ (từ JwtService)",
            expectedExceptions = AccessDeniedException.class)
    public void getRefreshToken_InvalidToken() {
        // Arrange
        String invalidToken = "invalid.token";
        when(jwtService.extractUsername(eq(invalidToken), eq(TokenType.REFRESH_TOKEN)))
                .thenThrow(new AccessDeniedException("Invalid JWT"));
        // Act
        authenticationService.getRefreshToken(invalidToken);
        // Assert: Implicit
    }

    @Test(description = "getRefreshToken: Thất bại - User không tìm thấy",
            expectedExceptions = UsernameNotFoundException.class)
    public void getRefreshToken_UserNotFound() {
        // Arrange
        String validToken = "valid.token.user.not.found";
        String usernameFromToken = "nonexistentuser";
        when(jwtService.extractUsername(eq(validToken), eq(TokenType.REFRESH_TOKEN)))
                .thenReturn(usernameFromToken);
        when(userRepository.findByUsername(eq(usernameFromToken)))
                .thenReturn(Optional.empty());
        // Act
        authenticationService.getRefreshToken(validToken);
        // Assert: Implicit
    }

    @Test(description = "getRefreshToken: Thất bại - User bị khóa (status != ACTIVE)",
            expectedExceptions = AccessDeniedException.class)
    public void getRefreshToken_UserDisabled() {
        // Arrange
        String validToken = "valid.token.user.disabled";
        userEntity.setStatus(UserStatus.INACTIVE);
        when(jwtService.extractUsername(eq(validToken), eq(TokenType.REFRESH_TOKEN)))
                .thenReturn(userEntity.getUsername());
        when(userRepository.findByUsername(eq(userEntity.getUsername())))
                .thenReturn(Optional.of(userEntity));
        // Act
        authenticationService.getRefreshToken(validToken);
        // Assert: Implicit
    }

    @Test(description = "getRefreshToken: Thất bại - Lỗi không mong muốn",
            expectedExceptions = AccessDeniedException.class)
    public void getRefreshToken_UnexpectedError() {
        // Arrange
        String validToken = "valid.token.unexpected.error";
        when(jwtService.extractUsername(eq(validToken), eq(TokenType.REFRESH_TOKEN)))
                .thenThrow(new RuntimeException("DB connection failed"));
        // Act
        authenticationService.getRefreshToken(validToken);
        // Assert: Implicit
    }
}