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
import org.springframework.security.core.userdetails.UserDetailsService; // Giữ lại import này
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Thêm import này để bắt lỗi
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
    // SỬA: Inject implementation đúng của UserDetailsService
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper; // Giữ nguyên nếu ObjectMapper được cung cấp bởi Spring

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
                // Cố gắng lấy username ngay cả khi token hết hạn (nếu extractUsername trả về null hoặc username)
                username = jwtService.extractUsername(token, TokenType.ACCESS_TOKEN);

                // Chỉ xử lý tiếp nếu có username và chưa có ai được xác thực trong context
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    log.debug("Username extracted from token: {}", username);

                    // SỬA: Sử dụng trực tiếp userDetailsService đã inject
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // SỬA: Thêm userDetails vào lời gọi isTokenValid
                    if (jwtService.isTokenValid(token, TokenType.ACCESS_TOKEN, userDetails)) {
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null, // Credentials thường là null khi dùng token
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        context.setAuthentication(authToken);
                        SecurityContextHolder.setContext(context);
                        log.debug("User {} authenticated successfully via JWT.", username);
                    } else {
                        // Token không hợp lệ mặc dù username có thể lấy được (ví dụ: hết hạn)
                        log.warn("JWT token is invalid or expired for user: {}", username);
                        // Không ném lỗi ở đây, chỉ không xác thực
                        // Nếu muốn trả lỗi ngay lập tức khi token hết hạn/không hợp lệ, bạn có thể gọi errorResponse
                        // errorResponse(response, "Invalid or expired token");
                        // return;
                    }
                } else if (username == null) {
                    log.warn("Could not extract username from JWT token.");
                    // Có thể trả lỗi nếu token có nhưng không lấy được username
                    // errorResponse(response, "Invalid token: Cannot extract username");
                    // return;
                } else {
                    log.debug("Security context already contains authentication for: {}", SecurityContextHolder.getContext().getAuthentication().getName());
                }

            } catch (AccessDeniedException e) {
                // Lỗi này thường xảy ra nếu token không đúng signature hoặc hết hạn (tùy cách implement extractUsername)
                log.warn("Access Denied while processing JWT token: {}", e.getMessage());
                errorResponse(response, e.getMessage()); // Trả lỗi 403
                return; // Dừng filter chain
            } catch (UsernameNotFoundException e) {
                // User có trong token nhưng không tìm thấy trong DB
                log.warn("User '{}' found in token but not in database.", username);
                errorResponse(response, "User associated with token not found");
                return; // Dừng filter chain
            } catch (Exception e) {
                // Các lỗi không mong muốn khác
                log.error("Unexpected error during JWT filter processing for user '{}': {}", username, e.getMessage(), e);
                errorResponse(response, "Authentication processing error");
                return; // Dừng filter chain
            }
        } else {
            log.debug("No JWT token found in Authorization header.");
        }

        // Tiếp tục filter chain nếu không có lỗi hoặc không có token
        filterChain.doFilter(request, response);
    }

    // Phương thức errorResponse giữ nguyên
    private void errorResponse(HttpServletResponse response, String message) throws IOException {
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(new Date());
        error.setError("Forbidden"); // Hoặc "Unauthorized" tùy ngữ cảnh
        error.setStatus(HttpServletResponse.SC_FORBIDDEN); // Hoặc SC_UNAUTHORIZED
        error.setMessage(message);

        response.setStatus(error.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }

    // Inner class ErrorResponse giữ nguyên
    @Setter
    @Getter
    private static class ErrorResponse {
        private Date timestamp;
        private int status;
        private String error;
        private String message;
    }
}