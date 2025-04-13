package com.backend.config;

import com.backend.common.TokenType;
import com.backend.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;


@Component
@RequiredArgsConstructor
@Slf4j(topic = "CUSTOMIZE-FILTER")
public class CustonmizeRequestFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper; // Provided by Spring

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        log.info("Processing request: {} {}", request.getMethod(), request.getRequestURI());

        final String authHeader = request.getHeader(AUTHORIZATION);

        if (StringUtils.hasLength(authHeader) && authHeader.startsWith("Bearer ")) {
            final String token = authHeader.substring(7);
            log.debug("Extracted JWT token: {}...", token.substring(0, Math.min(token.length(), 20)));

            String username = null;
            try {
                // Attempt to extract username even if token might be expired (depending on JwtService implementation)
                username = jwtService.extractUsername(token, TokenType.ACCESS_TOKEN);

                // Process only if username is extracted and no authentication exists in the context yet
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    log.debug("Username extracted from token: {}", username);

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // Validate the token (checks expiration and signature) against the loaded UserDetails
                    if (jwtService.isTokenValid(token, TokenType.ACCESS_TOKEN, userDetails)) {
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null, // Credentials are null for token-based auth
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        context.setAuthentication(authToken);
                        SecurityContextHolder.setContext(context);
                        log.debug("User {} authenticated successfully via JWT.", username);
                    } else {
                        // Token is invalid (e.g., expired) even though username was extractable
                        log.warn("JWT token is invalid or expired for user: {}", username);
                        // Do not throw an error here, just don't authenticate.
                        // If immediate error response on invalid/expired token is desired, call errorResponse here.
                        // errorResponse(response, "Invalid or expired token");
                        // return;
                    }
                } else if (username == null) {
                    log.warn("Could not extract username from JWT token.");
                    // Optionally return an error if a token exists but username extraction fails
                    // errorResponse(response, "Invalid token: Cannot extract username");
                    // return;
                } else {
                    // Authentication already exists in the context
                    log.debug("Security context already contains authentication for: {}", SecurityContextHolder.getContext().getAuthentication().getName());
                }

            } catch (AccessDeniedException e) {
                // Typically occurs due to invalid signature or expiration (depending on JwtService)
                log.warn("Access Denied while processing JWT token: {}", e.getMessage());
                errorResponse(response, e.getMessage()); // Return 403 Forbidden
                return; // Stop filter chain
            } catch (UsernameNotFoundException e) {
                // User exists in token but not found in the database
                log.warn("User '{}' found in token but not in database.", username);
                errorResponse(response, "User associated with token not found");
                return; // Stop filter chain
            } catch (Exception e) {
                // Catch other unexpected errors
                log.error("Unexpected error during JWT filter processing for user '{}': {}", username, e.getMessage(), e);
                errorResponse(response, "Authentication processing error");
                return; // Stop filter chain
            }
        } else {
            log.debug("No JWT token found in Authorization header.");
        }

        // Continue the filter chain if no error occurred or no token was present
        filterChain.doFilter(request, response);
    }

    // Helper method to send an error response
    private void errorResponse(HttpServletResponse response, String message) throws IOException {
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(new Date());
        error.setError("Forbidden"); // Or "Unauthorized" depending on context
        error.setStatus(HttpServletResponse.SC_FORBIDDEN); // Or SC_UNAUTHORIZED
        error.setMessage(message);

        response.setStatus(error.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }

    // Inner class for standard error response structure
    @Setter
    @Getter
    private static class ErrorResponse {
        private Date timestamp;
        private int status;
        private String error;
        private String message;
    }
}
