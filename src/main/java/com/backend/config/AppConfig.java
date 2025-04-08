// src/main/java/com/backend/config/AppConfig.java
package com.backend.config;

// << THÊM IMPORT NÀY (Package có thể cần điều chỉnh) >>
import com.backend.security.handler.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired; // << THÊM IMPORT NÀY >>
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class AppConfig {

    private final CustonmizeRequestFilter requestFilter;
    private final UserDetailsService userDetailsService;

    @Lazy
    @Autowired
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults()) // Enable CORS using the Bean below
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        .requestMatchers( // Public endpoints (Giữ nguyên)
                                "/auth/**",
                                "/api/v1/users/verify",
                                "/api/v1/users/set-initial-password",
                                // Swagger UI & Actuator endpoints
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**",
                                "/swagger-resources/**",
                                "/favicon.ico",
                                // Đảm bảo các endpoint cho OAuth2 được permit
                                "/login**", "/oauth2/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, // Public GET endpoints (Giữ nguyên)
                                "/api/v1/products", "/api/v1/products/**",
                                "/api/v1/categories", "/api/v1/categories/**",
                                "/api/v1/reviews/**",
                                "/api/v1/products/{productId}/reviews"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll() // Allow public registration (Giữ nguyên)
                        .anyRequest().authenticated() // All other requests need authentication (Giữ nguyên)
                )
                // << THÊM CẤU HÌNH OAUTH2 LOGIN VÀO ĐÂY >>
                .oauth2Login(oauth2 -> oauth2
                                .successHandler(oAuth2LoginSuccessHandler) // Chỉ định handler khi login Google thành công
                        // .failureHandler(...) // Có thể thêm xử lý khi lỗi nếu cần
                )
                // << KẾT THÚC PHẦN THÊM OAUTH2 LOGIN >>
                .sessionManagement(manager -> manager.sessionCreationPolicy(STATELESS)) // Giữ nguyên
                .authenticationProvider(authenticationProvider()) // Giữ nguyên
                .addFilterBefore(requestFilter, UsernamePasswordAuthenticationFilter.class); // Giữ nguyên JWT Filter

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { // Giữ nguyên Bean này
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173")); // Cho phép frontend
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration); // Áp dụng cho /api/**
        source.registerCorsConfiguration("/auth/**", configuration); // Áp dụng cho /auth/**
        // Thêm dòng này để áp dụng cho cả OAuth2 callbacks nếu cần (thường không cần vì đã permitAll)
        // source.registerCorsConfiguration("/login/oauth2/**", configuration);

        return source;
    }


    @Bean // Giữ nguyên Bean này
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean // Giữ nguyên Bean này
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean // Giữ nguyên Bean này
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}