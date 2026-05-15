package com.ledgerlens.security;

import com.ledgerlens.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_SECRET =
            "404e635266556a586e3272357538782f413f4428472b4b6250645367566b5970";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setExpiration(900_000L);
        props.setRefreshExpiration(604_800_000L);
        jwtService = new JwtService(props);
    }

    @Test
    void accessToken_roundTrip_extractsCorrectClaims() {
        String token = jwtService.generateAccessToken(EMAIL, USER_ID);

        assertThat(jwtService.extractEmailIfValid(token)).isEqualTo(EMAIL);
        assertThat(jwtService.extractUserId(token)).isEqualTo(USER_ID);
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void refreshToken_hasDifferentJti_thanAccessToken() {
        String access = jwtService.generateAccessToken(EMAIL, USER_ID);
        String refresh = jwtService.generateRefreshToken(EMAIL, USER_ID);

        assertThat(jwtService.extractJti(access)).isNotEqualTo(jwtService.extractJti(refresh));
    }

    @Test
    void tamperedToken_isInvalid() {
        String token = jwtService.generateAccessToken(EMAIL, USER_ID);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThat(jwtService.extractEmailIfValid(tampered)).isNull();
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void expiredToken_isInvalid() {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret(TEST_SECRET);
        shortLived.setExpiration(-1000L);
        shortLived.setRefreshExpiration(604_800_000L);
        JwtService shortService = new JwtService(shortLived);

        String token = shortService.generateAccessToken(EMAIL, USER_ID);

        assertThat(jwtService.extractEmailIfValid(token)).isNull();
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void extractClaims_onInvalidToken_throwsJwtException() {
        assertThatExceptionOfType(JwtException.class)
                .isThrownBy(() -> jwtService.extractClaims("not.a.token"));
    }
}
