package com.backend.service;

import com.backend.common.TokenType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails; // Thêm import UserDetails

import java.util.Collection;

public interface JwtService {

    String generateAccessToken(long userId, String username, Collection<? extends GrantedAuthority> authorities);

    String generateRefreshToken(long userId, String username, Collection<? extends GrantedAuthority> authorities);

    String extractUsername(String token, TokenType type);

    // Sửa signature của phương thức này để khớp với implementation
    boolean isTokenValid(String token, TokenType tokenType, UserDetails userDetails);
}