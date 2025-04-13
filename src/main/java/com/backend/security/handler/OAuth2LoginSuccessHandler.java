package com.backend.security.handler;

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
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;

    @Value("${app.frontend-url}") // Inject frontend URL from properties
    private String frontendUrl;

    @Value("${app.oauth2.redirect-path}") // Inject frontend redirect path from properties
    private String redirectPath;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 Login successful. Processing user details...");

        DefaultOAuth2User oauthUser = (DefaultOAuth2User) authentication.getPrincipal();

        // Extract standard user attributes provided by the OAuth2 provider (e.g., Google)
        String email = oauthUser.getAttribute("email");
        String firstName = oauthUser.getAttribute("given_name");
        String lastName = oauthUser.getAttribute("family_name");
        String pictureUrl = oauthUser.getAttribute("picture");

        if (email == null) {
            log.error("Could not get email from OAuth2 user attributes. Authentication: {}", authentication);
            // Redirect to frontend with an error parameter
            String errorUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                    .path(redirectPath) // Or a dedicated error page path
                    .queryParam("error", "Email not found from provider")
                    .build().toUriString();
            response.sendRedirect(errorUrl);
            return;
        }

        // Find or create the user in the local database
        UserEntity localUser = userService.processOAuthPostLogin(email, firstName, lastName, pictureUrl);

        // Generate JWT access token for the authenticated user
        String accessToken = jwtService.generateAccessToken(localUser.getId(), localUser.getUsername(), localUser.getAuthorities());
        // Optionally generate a refresh token if needed
        // String refreshToken = jwtService.generateRefreshToken(localUser.getId(), localUser.getUsername(), localUser.getAuthorities());

        log.info("Generated JWT Access Token for user: {}", localUser.getUsername());

        // Build the target URL for redirecting back to the frontend application
        // Append the access token (and optionally refresh token) as query parameters
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .path(redirectPath)
                .queryParam("token", accessToken)
                // .queryParam("refresh_token", refreshToken) // Uncomment if sending refresh token
                .build().toUriString();

        log.info("Redirecting user {} to frontend: {}", localUser.getUsername(), targetUrl);

        // Perform the redirect
        response.sendRedirect(targetUrl);
    }

    // Removed commented-out clearAuthenticationAttributes method
}
