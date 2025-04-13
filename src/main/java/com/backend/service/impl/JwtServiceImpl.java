package com.backend.service.impl;

import com.backend.common.TokenType;
import com.backend.exception.InvalidDataException;
import com.backend.service.JwtService;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

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
        claims.put("role", authorities);

        return generateToken(claims, username);
    }

    @Override
    public String generateRefreshToken(long userId, String username, Collection<? extends GrantedAuthority> authorities) {
        log.info("Generate refresh token for user {}", userId); // Made log more specific

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", authorities);

        return generateRefreshToken(claims, username);
    }

    @Override
    public String extractUsername(String token, TokenType type) {
        try {
            return extractClaim(token, type, Claims::getSubject);
        } catch (ExpiredJwtException e) {
            // If the token is expired, we might still want the username for refresh logic,
            // but for general validation, returning null or throwing is appropriate.
            // Let's return null for now, assuming validation handles this.
            log.warn("Attempted to extract username from expired {} token for subject", type);
            return null;
        } catch (Exception e) {
            log.error("Error extracting username from {} token: {}", type, e.getMessage());
            return null; // Return null on other extraction errors
        }
    }

    @Override // Added missing @Override annotation
    public boolean isTokenValid(String token, TokenType tokenType, UserDetails userDetails) {
        log.info("Validating {} token for user {}", tokenType, userDetails.getUsername());
        try {
            final String username = extractUsername(token, tokenType);
            // Check if username matches and token is not expired
            boolean isValid = username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token, tokenType);
            log.info("Token validation result for user {}: {}", userDetails.getUsername(), isValid);
            return isValid;
        } catch (AccessDeniedException e) {
            // This can be thrown by extractAllClaims if parsing fails (e.g., signature mismatch)
            log.warn("Token validation failed for user {} (Access Denied): {}", userDetails.getUsername(), e.getMessage());
            return false;
        } catch (Exception e) {
            // Catch any other unexpected errors during validation
            log.error("Unexpected error during token validation for user {}: {}", userDetails.getUsername(), e.getMessage(), e); // Log exception details
            return false;
        }
    }

    // Helper method to check if the token is expired
    private boolean isTokenExpired(String token, TokenType type) {
        try {
            Date expiration = extractClaim(token, type, Claims::getExpiration);
            boolean isExpired = expiration != null && expiration.before(new Date());
            if (isExpired) {
                log.warn("{} token expired at {}", type, expiration);
            }
            return isExpired;
        } catch (ExpiredJwtException e) {
            // If extractClaim itself throws ExpiredJwtException, it's definitely expired
            log.warn("{} token is confirmed expired (caught ExpiredJwtException during expiration check)", type);
            return true;
        } catch (Exception e) {
            // If any other error occurs while getting expiration, treat as invalid/expired
            log.error("Could not determine expiration for {} token: {}", type, e.getMessage(), e);
            return true; // Treat as expired/invalid if expiration cannot be determined
        }
    }


    private String generateToken(Map<String, Object> claims, String username) {
        log.debug("Generating access token for subject: {}", username); // Changed to debug level
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * expiryMinutes))
                .signWith(getKey(ACCESS_TOKEN), SignatureAlgorithm.HS256)
                .compact();
    }

    private String generateRefreshToken(Map<String, Object> claims, String username) {
        log.debug("Generating refresh token for subject: {}", username); // Changed to debug level
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * expiryDay))
                .signWith(getKey(REFRESH_TOKEN), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getKey(TokenType type) {
        log.debug("Retrieving signing key for token type: {}", type); // Changed to debug level
        switch (type) {
            case ACCESS_TOKEN -> {
                if (!StringUtils.hasText(accessKey)) throw new InvalidDataException("Access key is not configured");
                return Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessKey));
            }
            case REFRESH_TOKEN -> {
                 if (!StringUtils.hasText(refreshKey)) throw new InvalidDataException("Refresh key is not configured");
                return Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshKey));
            }
            default -> throw new InvalidDataException("Invalid token type specified for key retrieval");
        }
    }

    private <T> T extractClaim(String token, TokenType type, Function<Claims, T> claimResolver) {
        log.debug("Extracting claim using provided resolver for token type: {}", type); // Changed to debug level
        final Claims claims = extractAllClaims(token, type);
        return claimResolver.apply(claims);
    }

    private Claims extractAllClaims(String token, TokenType type) {
         log.debug("Parsing all claims for token type: {}", type); // Changed to debug level
        try {
            return Jwts.parserBuilder().setSigningKey(getKey(type)).build().parseClaimsJws(token).getBody();
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature for {} token: {}", type, e.getMessage());
            throw new AccessDeniedException("Access denied: Invalid signature");
        } catch (ExpiredJwtException e) {
             log.warn("Expired JWT {} token: {}", type, e.getMessage());
             // Re-throw specifically for handling expiration if needed upstream,
             // otherwise AccessDeniedException is also suitable. Let's keep AccessDenied for consistency here.
             // throw e;
             throw new AccessDeniedException("Access denied: Token expired");
        } catch (Exception e) { // Catch other parsing exceptions (MalformedJwtException, UnsupportedJwtException, etc.)
            log.error("Error parsing {} token: {}", type, e.getMessage(), e);
            throw new AccessDeniedException("Access denied: Invalid token");
        }
    }

    // Removed commented out old isTokenValid method
}
