package com.backend.service.impl;

import com.backend.common.TokenType; // Giữ lại package đúng của bạn
import com.backend.exception.InvalidDataException; // Giữ lại package đúng của bạn
import com.backend.service.JwtService; // Giữ lại package đúng của bạn
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails; // Import UserDetails
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils; // Import StringUtils

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// Import static trực tiếp từ package đúng của bạn
import static com.backend.common.TokenType.ACCESS_TOKEN;
import static com.backend.common.TokenType.REFRESH_TOKEN;


@Service
@Slf4j(topic = "JWT-SERVICE")
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.expiryMinutes}")
    private long expiryMinutes;

    @Value("${jwt.expiryDay}")
    private long expiryDay;

    @Value("${jwt.accessKey}")
    private String accessKey;

    @Value("${jwt.refreshKey}")
    private String refreshKey;

    @Override
    public String generateAccessToken(long userId, String username, Collection<? extends GrantedAuthority> authorities) {
        log.info("Generate access token for user {} with authorities {}", userId, authorities);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        // Giống ví dụ: Đưa trực tiếp Collection vào claims
        claims.put("role", authorities);

        return generateToken(claims, username);
    }

    @Override
    public String generateRefreshToken(long userId, String username, Collection<? extends GrantedAuthority> authorities) {
        // Sửa log giống ví dụ
        log.info("Generate refresh token");

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        // Giống ví dụ: Đưa trực tiếp Collection vào claims
        claims.put("role", authorities);

        return generateRefreshToken(claims, username);
    }

    @Override
    public String extractUsername(String token, TokenType type) {
        try {
            return extractClaim(token, type, Claims::getSubject);
        } catch (ExpiredJwtException e) {
            log.warn("Attempted to extract username from expired {} token", type);
            // Trả về username từ token hết hạn nếu cần cho logic nào đó,
            // hoặc ném lại lỗi / trả về null tùy yêu cầu.
            // Ví dụ: return e.getClaims().getSubject();
            return null; // Hoặc ném lại lỗi nếu không muốn xử lý token hết hạn ở đây
        } catch (Exception e) {
            log.error("Error extracting username from {} token: {}", type, e.getMessage());
            return null;
        }
    }

    // *** PHƯƠNG THỨC CẦN VIẾT TIẾP ***
    // Có thể thêm UserDetails để kiểm tra xem username trong token có khớp với UserDetails không
    public boolean isTokenValid(String token, TokenType tokenType, UserDetails userDetails) {
        log.info("Validating {} token for user {}", tokenType, userDetails.getUsername());
        try {
            final String username = extractUsername(token, tokenType);
            // Kiểm tra username từ token có khớp và token chưa hết hạn
            boolean isValid = username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token, tokenType);
            log.info("Token validation result for user {}: {}", userDetails.getUsername(), isValid);
            return isValid;
        } catch (AccessDeniedException e) {
            // Lỗi AccessDeniedException từ extractUsername (do extraAllClaims ném ra)
            log.warn("Token validation failed for user {}: {}", userDetails.getUsername(), e.getMessage());
            return false;
        } catch (Exception e) {
            // Các lỗi khác không mong muốn
            log.error("Unexpected error during token validation for user {}: {}", userDetails.getUsername(), e.getMessage());
            return false;
        }
    }

    // Phương thức helper để kiểm tra token hết hạn
    private boolean isTokenExpired(String token, TokenType type) {
        try {
            Date expiration = extractClaim(token, type, Claims::getExpiration);
            boolean isExpired = expiration != null && expiration.before(new Date());
            if (isExpired) {
                log.warn("{} token expired at {}", type, expiration);
            }
            return isExpired;
        } catch (ExpiredJwtException e) {
            // Nếu extractClaim ném ExpiredJwtException thì chắc chắn đã hết hạn
            log.warn("{} token is confirmed expired (caught ExpiredJwtException)", type);
            return true;
        } catch (Exception e){
            // Nếu có lỗi khác khi lấy expiration, coi như không hợp lệ
            log.error("Could not determine expiration for {} token: {}", type, e.getMessage());
            return true; // Coi như hết hạn/không hợp lệ nếu không lấy được ngày hết hạn
        }
    }


    // === PHẦN CÒN LẠI GIỮ NGUYÊN NHƯNG SỬA LỖI CHÍNH TẢ ===

    private String generateToken(Map<String, Object> claims, String username) {
        log.info("----------[ generateToken ]----------");
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * expiryMinutes))
                // Truyền ACCESS_TOKEN trực tiếp
                .signWith(getKey(ACCESS_TOKEN), SignatureAlgorithm.HS256)
                .compact();
    }

    private String generateRefreshToken(Map<String, Object> claims, String username) {
        log.info("----------[ generateRefreshToken ]----------");
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * expiryDay))
                // Truyền REFRESH_TOKEN trực tiếp
                .signWith(getKey(REFRESH_TOKEN), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getKey(TokenType type) {
        log.info("----------[ getKey ]----------");
        // Dùng switch expression (->)
        switch (type) {
            case ACCESS_TOKEN -> {
                return Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessKey));
            }
            case REFRESH_TOKEN -> {
                return Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshKey));
            }
            // Ném InvalidDataException
            default -> throw new InvalidDataException("Invalid token type");
        }
    }

    private <T> T extractClaim(String token, TokenType type, Function<Claims, T> claimResolver) {
        log.info("----------[ extractClaim ]----------");
        // Gọi phương thức đã sửa lỗi chính tả "extractAllClaims"
        final Claims claims = extractAllClaims(token, type);
        return claimResolver.apply(claims);
    }

    // Sửa lỗi chính tả "extraAllClaim" thành "extractAllClaims"
    private Claims extractAllClaims(String token, TokenType type) {
        log.info("----------[ extractAllClaims ]----------"); // Sửa log
        try {
            return Jwts.parserBuilder().setSigningKey(getKey(type)).build().parseClaimsJws(token).getBody();
        } catch (SignatureException | ExpiredJwtException e) { // Invalid signature or expired token
            // Ném AccessDeniedException với message gốc
            log.warn("Failed to parse {} token: {}", type, e.getMessage());
            throw new AccessDeniedException("Access denied: " + e.getMessage());
        } catch (Exception e) {
            // Bắt các lỗi parsing khác
            log.error("Unexpected error parsing {} token: {}", type, e.getMessage());
            throw new AccessDeniedException("Access denied: Invalid token format or other parsing error.");
        }
    }

    // *** XÓA PHIÊN BẢN isTokenValid CŨ TRẢ VỀ FALSE ***
    // @Override
    // public boolean isTokenValid(String token, TokenType tokenType) {
    //     return false;
    // }
}