package com.ledgerlens.auth;

import com.ledgerlens.security.JwtService;
import com.ledgerlens.security.TokenService;
import com.ledgerlens.security.RateLimitService;
import com.ledgerlens.user.User;
import com.ledgerlens.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final RateLimitService rateLimitService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().toLowerCase();

        if (!rateLimitService.tryConsume(email)) {
            log.warn("AUTH register rate-limited: {}", email);
            return ResponseEntity.status(429).body("Too many attempts. Try again later.");
        }

        if (userRepository.existsByEmail(email)) {
            log.warn("AUTH register failed - email exists: {}", email);
            return ResponseEntity.badRequest().body("Email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("AUTH register failed - concurrent duplicate email: {}", email);
            return ResponseEntity.badRequest().body("Email already exists");
        }

        String accessToken = jwtService.generateAccessToken(email, user.getId());
        String refreshToken = jwtService.generateRefreshToken(email, user.getId());
        tokenService.storeRefreshToken(jwtService.extractJti(refreshToken), email);

        log.info("AUTH register success: userId={}", user.getId());
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String email = request.email().toLowerCase();

        if (!rateLimitService.tryConsume(email)) {
            log.warn("AUTH login rate-limited: {}", email);
            return ResponseEntity.status(429).body("Too many attempts. Try again later.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("AUTH login failed - user not found: {}", email);
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("AUTH login failed - wrong password: userId={}", user.getId());
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(email, user.getId());
        String refreshToken = jwtService.generateRefreshToken(email, user.getId());
        tokenService.storeRefreshToken(jwtService.extractJti(refreshToken), email);

        log.info("AUTH login success: userId={}", user.getId());
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        String token = request.refreshToken();

        // Parse once — all three fields come from the same verified Claims object
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtService.extractClaims(token);
        } catch (io.jsonwebtoken.JwtException e) {
            return ResponseEntity.status(401).body("Invalid refresh token");
        }

        // Normalise to match how emails are stored (register/login both lowercase)
        String email = claims.getSubject().toLowerCase();
        UUID userId = UUID.fromString((String) claims.get("uid"));
        String jti = claims.getId();

        if (!userRepository.existsByEmail(email)) {
            log.warn("AUTH refresh failed - user not found: {}", email);
            return ResponseEntity.status(401).body("User not found");
        }

        if (!tokenService.validateAndDeleteRefreshToken(jti, email)) {
            // Token was already consumed — possible theft. Invalidate all live sessions.
            tokenService.invalidateUserSessions(userId);
            log.warn("AUTH refresh - token reuse detected, sessions invalidated: userId={}", userId);
            return ResponseEntity.status(401).body("Refresh token reuse detected. Please login again.");
        }

        String newAccessToken = jwtService.generateAccessToken(email, userId);
        String newRefreshToken = jwtService.generateRefreshToken(email, userId);
        tokenService.storeRefreshToken(jwtService.extractJti(newRefreshToken), email);

        log.info("AUTH refresh success: userId={}", userId);
        return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
                                    @RequestBody(required = false) RefreshRequest request) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            if (jwtService.isTokenValid(accessToken)) {
                long remainingMillis = jwtService.getExpirationDate(accessToken).getTime() - new Date().getTime();
                tokenService.blacklistToken(accessToken, remainingMillis);
            }
        }

        // Best-effort: delete refresh token if provided and valid — never fail logout
        if (request != null && request.refreshToken() != null) {
            String email = jwtService.extractEmailIfValid(request.refreshToken());
            if (email != null) {
                String jti = jwtService.extractJti(request.refreshToken());
                tokenService.deleteRefreshToken(jti);
            }
        }

        log.info("AUTH logout");
        return ResponseEntity.ok("Logged out successfully");
    }
}
