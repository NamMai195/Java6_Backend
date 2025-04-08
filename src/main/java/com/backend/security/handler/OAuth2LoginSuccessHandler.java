package com.backend.security.handler; // Thay package cho phù hợp

import com.backend.model.UserEntity;
import com.backend.service.JwtService;
import com.backend.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User; // Dùng DefaultOAuth2User để lấy attributes
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component // Đánh dấu là một Spring Bean
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;

    // Lấy URL frontend từ application.yml để redirect về
    @Value("${app.frontend-url:http://localhost:5173}") // Thêm URL frontend vào application.yml
    private String frontendUrl;

    @Value("${app.oauth2.redirect-path:/logincallback}") // Thêm path callback trên frontend vào application.yml
    private String redirectPath;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 Login successful. Processing user details...");

        // Principal sau khi login OAuth2 thành công thường là DefaultOAuth2User
        DefaultOAuth2User oauthUser = (DefaultOAuth2User) authentication.getPrincipal();

        // Lấy các thuộc tính từ Google (tên thuộc tính có thể thay đổi một chút tùy cấu hình)
        String email = oauthUser.getAttribute("email");
        String firstName = oauthUser.getAttribute("given_name");
        String lastName = oauthUser.getAttribute("family_name");
        String pictureUrl = oauthUser.getAttribute("picture"); // URL ảnh đại diện

        if (email == null) {
            log.error("Could not get email from OAuth2 user attributes. Authentication: {}", authentication);
            // Có thể redirect về trang lỗi trên frontend
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                    .path(redirectPath) // Hoặc trang lỗi riêng
                    .queryParam("error", "Email not found from provider")
                    .build().toUriString());
            return;
        }

        // Gọi UserService để tìm hoặc tạo user trong DB của mình
        UserEntity localUser = userService.processOAuthPostLogin(email, firstName, lastName, pictureUrl);

        // Tạo JWT token cho user đã được xử lý (tạo mới hoặc tìm thấy)
        // Dùng Access Token để trả về cho frontend
        String accessToken = jwtService.generateAccessToken(localUser.getId(), localUser.getUsername(), localUser.getAuthorities());
        // Có thể tạo cả Refresh Token nếu cần
        // String refreshToken = jwtService.generateRefreshToken(localUser.getId(), localUser.getUsername(), localUser.getAuthorities());

        log.info("Generated JWT Access Token for user: {}", localUser.getUsername());

        // Tạo URL để redirect về frontend, đính kèm token
        // Ví dụ: http://localhost:5173/logincallback?token=xxx
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .path(redirectPath)
                .queryParam("token", accessToken)
                // .queryParam("refresh_token", refreshToken) // Gửi cả refresh token nếu cần
                .build().toUriString();

        log.info("Redirecting user {} to frontend: {}", localUser.getUsername(), targetUrl);

        // Thực hiện redirect
        // Xóa các attribute không cần thiết trong session nếu có
        // clearAuthenticationAttributes(request); // Nếu cần
        response.sendRedirect(targetUrl);
    }

    // Optional: Hàm xóa attribute session (nếu dùng session trước đó)
    /*
    protected final void clearAuthenticationAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
    }
    */
}