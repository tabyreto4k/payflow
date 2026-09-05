package ru.payflow.order.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.payflow.order.auth.model.Role;
import ru.payflow.order.auth.model.User;
import ru.payflow.order.config.JwtProperties;
import ru.payflow.order.exception.InvalidTokenException;

class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-key-32-bytes-min";
    private static final SecretKeySpec KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));
    private final JwtDecoder decoder =
            NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();

    private TokenService tokenService(Duration accessTtl, Duration refreshTtl) {
        return new TokenService(encoder, decoder, new JwtProperties(SECRET, accessTtl, refreshTtl));
    }

    @Test
    void accessTokenCarriesUserIdAndRole() {
        User user = user();

        var jwt = decoder.decode(
                tokenService(Duration.ofMinutes(15), Duration.ofDays(7)).issueAccess(user));

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("roles")).isEqualTo("USER");
        assertThat(jwt.getClaimAsString("type")).isEqualTo("access");
    }

    @Test
    void refreshTokenResolvesToItsOwner() {
        User user = user();
        TokenService tokens = tokenService(Duration.ofMinutes(15), Duration.ofDays(7));

        assertThat(tokens.subjectOfRefresh(tokens.issueRefresh(user))).isEqualTo(user.getId());
    }

    @Test
    void accessTokenIsNotAcceptedAsRefresh() {
        TokenService tokens = tokenService(Duration.ofMinutes(15), Duration.ofDays(7));
        String access = tokens.issueAccess(user());

        assertThatExceptionOfType(InvalidTokenException.class).isThrownBy(() -> tokens.subjectOfRefresh(access));
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        Instant longAgo = Instant.now().minus(Duration.ofDays(30));
        JwtClaimsSet expired = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(longAgo)
                .expiresAt(longAgo.plus(Duration.ofDays(7)))
                .claim("type", "refresh")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), expired))
                .getTokenValue();

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() ->
                        tokenService(Duration.ofMinutes(15), Duration.ofDays(7)).subjectOfRefresh(token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        SecretKeySpec other =
                new SecretKeySpec("another-secret-key-32-bytes-min!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        TokenService alien = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(other)),
                decoder,
                new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7)));
        String forged = alien.issueRefresh(user());

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() ->
                        tokenService(Duration.ofMinutes(15), Duration.ofDays(7)).subjectOfRefresh(forged));
    }

    private static User user() {
        User user = new User("ivan@payflow.ru", "$2a$10$hash", Role.USER);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
