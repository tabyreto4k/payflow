package ru.payflow.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthFilterTest {

    private static final SecretKeySpec KEY =
            new SecretKeySpec("gateway-unit-test-secret-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));
    private final JwtDecoder decoder =
            NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    private final JwtAuthFilter filter = new JwtAuthFilter(decoder, new ObjectMapper());

    @Test
    void validTokenBecomesIdentityHeaders() {
        UUID customerId = UUID.randomUUID();
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(customerId, "access", Duration.ofMinutes(15))));

        ServerWebExchange passed = passThrough(exchange);

        assertThat(passed.getRequest().getHeaders().getFirst(JwtAuthFilter.CUSTOMER_ID_HEADER))
                .isEqualTo(customerId.toString());
        assertThat(passed.getRequest().getHeaders().getFirst(JwtAuthFilter.ROLES_HEADER))
                .isEqualTo("USER");
    }

    @Test
    void clientSuppliedIdentityHeadersAreStripped() {
        UUID customerId = UUID.randomUUID();
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(customerId, "access", Duration.ofMinutes(15)))
                .header(JwtAuthFilter.CUSTOMER_ID_HEADER, UUID.randomUUID().toString())
                .header(JwtAuthFilter.ROLES_HEADER, "ADMIN"));

        ServerWebExchange passed = passThrough(exchange);

        assertThat(passed.getRequest().getHeaders().get(JwtAuthFilter.CUSTOMER_ID_HEADER))
                .containsExactly(customerId.toString());
        assertThat(passed.getRequest().getHeaders().get(JwtAuthFilter.ROLES_HEADER))
                .containsExactly("USER");
    }

    @Test
    void spoofedHeadersOnPublicPathAreStrippedToo() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/auth/login")
                .header(JwtAuthFilter.CUSTOMER_ID_HEADER, UUID.randomUUID().toString())
                .header(JwtAuthFilter.ROLES_HEADER, "ADMIN"));

        ServerWebExchange passed = passThrough(exchange);

        assertThat(passed.getRequest().getHeaders().get(JwtAuthFilter.CUSTOMER_ID_HEADER))
                .isNull();
        assertThat(passed.getRequest().getHeaders().get(JwtAuthFilter.ROLES_HEADER))
                .isNull();
    }

    @Test
    void requestWithoutTokenIsRejected() {
        assertThat(rejectionBody(MockServerHttpRequest.get("/api/v1/orders"))).contains("Authorization");
    }

    @Test
    void expiredTokenIsRejected() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String expired = token(UUID.randomUUID(), "access", issuedAt, issuedAt.plus(Duration.ofMinutes(15)));

        assertThat(rejectionBody(MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)))
                .contains("просрочен");
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(rejectionBody(MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer nonsense")))
                .contains("недействителен");
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccess() {
        assertThat(rejectionBody(MockServerHttpRequest.get("/api/v1/orders")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(UUID.randomUUID(), "refresh", Duration.ofDays(7)))))
                .contains("access-токен");
    }

    private ServerWebExchange passThrough(MockServerWebExchange exchange) {
        ServerWebExchange[] seen = new ServerWebExchange[1];
        filter.filter(exchange, passed -> {
                    seen[0] = passed;
                    return Mono.empty();
                })
                .block();
        assertThat(seen[0]).as("запрос должен уйти дальше по цепочке").isNotNull();
        return seen[0];
    }

    private String rejectionBody(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = exchange(request);
        filter.filter(exchange, passed -> Mono.error(new AssertionError("запрос не должен был уйти дальше")))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().getBodyAsString().block();
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private String token(UUID subject, String type, Duration ttl) {
        Instant now = Instant.now();
        return token(subject, type, now, now.plus(ttl));
    }

    private String token(UUID subject, String type, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("type", type)
                .claim("roles", "USER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
