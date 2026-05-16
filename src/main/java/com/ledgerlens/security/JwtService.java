package com.ledgerlens.security;

import com.ledgerlens.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.startsWith("${") || secret.length() < 64) {
            throw new IllegalStateException(
                    "JWT_SECRET is not configured or is too short. " +
                    "Set the JWT_SECRET environment variable to a 32-byte (64 hex char) random value. " +
                    "For local dev, activate the 'local' Spring profile: -Dspring.profiles.active=local");
        }
        this.signingKey = Keys.hmacShaKeyFor(HexFormat.of().parseHex(secret));
    }

    public String generateAccessToken(String email, UUID userId) {
        return buildToken(email, userId, ACCESS_TOKEN_TYPE, jwtProperties.getExpiration());
    }

    public String generateRefreshToken(String email, UUID userId) {
        return buildToken(email, userId, REFRESH_TOKEN_TYPE, jwtProperties.getRefreshExpiration());
    }

    private String buildToken(String email, UUID userId, String tokenType, long expiration) {
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .claim("uid", userId.toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey)
                .compact();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString((String) extractClaims(token).get("uid"));
    }

    /**
     * Returns email if the token signature and expiry are valid, null otherwise.
     * Avoids leaking JwtException to callers — they just get null on any failure.
     */
    public String extractEmailIfValid(String token) {
        try {
            return extractClaims(token).getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    public String extractAccessEmailIfValid(String token) {
        return extractEmailIfValidForType(token, ACCESS_TOKEN_TYPE);
    }

    public Claims extractRefreshClaims(String token) {
        Claims claims = extractClaims(token);
        requireTokenType(claims, REFRESH_TOKEN_TYPE);
        return claims;
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isAccessTokenValid(String token) {
        return extractAccessEmailIfValid(token) != null;
    }

    public Date getExpirationDate(String token) {
        return extractClaims(token).getExpiration();
    }

    /**
     * Returns all claims from the token parsed and verified in a single pass.
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String extractEmailIfValidForType(String token, String expectedType) {
        try {
            Claims claims = extractClaims(token);
            requireTokenType(claims, expectedType);
            return claims.getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    private void requireTokenType(Claims claims, String expectedType) {
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedType.equals(tokenType)) {
            throw new JwtException("Invalid token type");
        }
    }
}
