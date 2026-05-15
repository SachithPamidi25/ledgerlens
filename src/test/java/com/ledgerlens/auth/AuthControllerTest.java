package com.ledgerlens.auth;

import com.ledgerlens.config.JwtProperties;
import com.ledgerlens.security.JwtService;
import com.ledgerlens.security.RateLimitService;
import com.ledgerlens.security.TokenService;
import com.ledgerlens.user.User;
import com.ledgerlens.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String SECRET =
            "404e635266556a586e3272357538782f413f4428472b4b6250645367566b5970";

    @Mock private UserRepository userRepository;
    @Mock private TokenService tokenService;
    @Mock private RateLimitService rateLimitService;

    private AuthController controller;
    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpiration(900_000L);
        props.setRefreshExpiration(604_800_000L);
        jwtService = new JwtService(props);
        passwordEncoder = new BCryptPasswordEncoder();
        controller = new AuthController(userRepository, passwordEncoder, jwtService, tokenService, rateLimitService);
    }

    @Test
    void register_newUser_returns200WithTokens() {
        when(rateLimitService.tryConsume(any())).thenReturn(true);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            // Simulate JPA assigning a generated ID on persist
            try {
                java.lang.reflect.Field idField = User.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(u, UUID.randomUUID());
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
            return u;
        });

        RegisterRequest req = new RegisterRequest("new@example.com", "Password1", "Test User");
        ResponseEntity<?> response = controller.register(req);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(AuthResponse.class);
        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
    }

    @Test
    void register_duplicateEmail_returns400() {
        when(rateLimitService.tryConsume(any())).thenReturn(true);
        when(userRepository.existsByEmail(any())).thenReturn(true);

        ResponseEntity<?> response = controller.register(
                new RegisterRequest("existing@example.com", "Password1", "Test User"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void register_rateLimited_returns429() {
        when(rateLimitService.tryConsume(any())).thenReturn(false);

        ResponseEntity<?> response = controller.register(
                new RegisterRequest("any@example.com", "Password1", "Test User"));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
    }

    private static User userWithId(String email, String encodedPassword, String fullName) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encodedPassword);
        u.setFullName(fullName);
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u, UUID.randomUUID());
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return u;
    }

    @Test
    void login_correctCredentials_returns200WithTokens() {
        String rawPassword = "Password1";
        User user = userWithId("user@example.com", passwordEncoder.encode(rawPassword), "User");

        when(rateLimitService.tryConsume(any())).thenReturn(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.login(
                new LoginRequest("user@example.com", rawPassword));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(AuthResponse.class);
    }

    @Test
    void login_wrongPassword_returns401() {
        User user = userWithId("user@example.com", passwordEncoder.encode("CorrectPass1"), "User");

        when(rateLimitService.tryConsume(any())).thenReturn(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.login(
                new LoginRequest("user@example.com", "WrongPass1"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void login_unknownEmail_returns401() {
        when(rateLimitService.tryConsume(any())).thenReturn(true);
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.login(
                new LoginRequest("nobody@example.com", "Password1"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void refresh_tokenReuse_invalidatesSessionsAndReturns401() {
        UUID userId = UUID.randomUUID();
        String email = "victim@example.com";
        String refreshToken = jwtService.generateRefreshToken(email, userId);

        when(userRepository.existsByEmail(email)).thenReturn(true);
        when(tokenService.validateAndDeleteRefreshToken(anyString(), eq(email))).thenReturn(false);

        ResponseEntity<?> response = controller.refresh(new RefreshRequest(refreshToken));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(tokenService).invalidateUserSessions(userId);
    }

    @Test
    void refresh_validToken_rotatesTokens() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        String refreshToken = jwtService.generateRefreshToken(email, userId);

        when(userRepository.existsByEmail(email)).thenReturn(true);
        when(tokenService.validateAndDeleteRefreshToken(anyString(), eq(email))).thenReturn(true);

        ResponseEntity<?> response = controller.refresh(new RefreshRequest(refreshToken));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotEqualTo(refreshToken);
    }
}
