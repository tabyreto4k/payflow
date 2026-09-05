package ru.payflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.payflow.gateway.security.JwtAuthFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT extends RedisIT {

    private static final String SECRET = "gateway-routing-it-secret-32-byte";

    private static final MockWebServer ORDERS = new MockWebServer();
    private static final MockWebServer PAYMENTS = new MockWebServer();

    private final JwtEncoder encoder = new NimbusJwtEncoder(
            new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) throws IOException {
        ORDERS.start();
        PAYMENTS.start();
        registry.add("payflow.jwt.secret", () -> SECRET);
        registry.add("ORDER_SERVICE_URI", () -> "http://localhost:" + ORDERS.getPort());
        registry.add("PAYMENT_SERVICE_URI", () -> "http://localhost:" + PAYMENTS.getPort());
        // Маршрутизацию проверяем без помех: своё ведро есть у RateLimitIT.
        registry.add("RATE_LIMIT_REPLENISH_RATE", () -> 1000);
        registry.add("RATE_LIMIT_BURST_CAPACITY", () -> 1000);
    }

    @AfterAll
    static void stopBackends() throws IOException {
        ORDERS.shutdown();
        PAYMENTS.shutdown();
    }

    @BeforeEach
    void enqueueOk() {
        ORDERS.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        PAYMENTS.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
    }

    @Test
    void ordersGoToOrderServiceWithIdentityHeaders() throws Exception {
        UUID customerId = UUID.randomUUID();

        client.get()
                .uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(customerId))
                .exchange()
                .expectStatus()
                .isOk();

        RecordedRequest forwarded = ORDERS.takeRequest(5, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/orders");
        assertThat(forwarded.getHeader(JwtAuthFilter.CUSTOMER_ID_HEADER)).isEqualTo(customerId.toString());
        assertThat(forwarded.getHeader(JwtAuthFilter.ROLES_HEADER)).isEqualTo("USER");
    }

    @Test
    void accountsGoToPaymentService() throws Exception {
        client.get()
                .uri("/api/v1/accounts/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(UUID.randomUUID()))
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(PAYMENTS.takeRequest(5, TimeUnit.SECONDS)).isNotNull();
        assertNothingReached(ORDERS);
    }

    @Test
    void authGoesThroughWithoutToken() throws Exception {
        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();

        assertThat(ORDERS.takeRequest(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void requestWithoutTokenIsRejectedWithProblemDetail() throws Exception {
        client.get()
                .uri("/api/v1/orders")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401)
                .jsonPath("$.title")
                .isEqualTo("Не аутентифицирован");

        assertNothingReached(ORDERS);
    }

    @Test
    void spoofedCustomerIdIsReplacedByTheOneFromToken() throws Exception {
        UUID owner = UUID.randomUUID();

        client.get()
                .uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                .header(JwtAuthFilter.CUSTOMER_ID_HEADER, UUID.randomUUID().toString())
                .header(JwtAuthFilter.ROLES_HEADER, "ADMIN")
                .exchange()
                .expectStatus()
                .isOk();

        RecordedRequest forwarded = ORDERS.takeRequest(5, TimeUnit.SECONDS);
        assertThat(forwarded.getHeaders().values(JwtAuthFilter.CUSTOMER_ID_HEADER))
                .containsExactly(owner.toString());
        assertThat(forwarded.getHeaders().values(JwtAuthFilter.ROLES_HEADER)).containsExactly("USER");
    }

    @Test
    void spoofedHeadersDoNotSurviveOnPublicPath() throws Exception {
        client.post()
                .uri("/api/v1/auth/login")
                .header(JwtAuthFilter.CUSTOMER_ID_HEADER, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(ORDERS.takeRequest(5, TimeUnit.SECONDS).getHeader(JwtAuthFilter.CUSTOMER_ID_HEADER))
                .isNull();
    }

    /** Счётчик запросов у MockWebServer общий на класс, поэтому смотрим не число, а очередь. */
    private static void assertNothingReached(MockWebServer backend) throws InterruptedException {
        assertThat(backend.takeRequest(300, TimeUnit.MILLISECONDS)).isNull();
    }

    private String accessToken(UUID customerId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(customerId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("type", "access")
                .claim("roles", "USER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
