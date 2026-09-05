package ru.payflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.UUID;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Ведро на анонимного клиента: без токена ключ — адрес, и все запросы теста попадают в одно ведро.
 * Ёмкость единица, чтобы второй запрос упирался в лимит сразу, без ожидания пополнения.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT extends RedisIT {

    private static final MockWebServer ORDERS = new MockWebServer();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void limits(DynamicPropertyRegistry registry) throws IOException {
        ORDERS.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200).setBody("{}");
            }
        });
        ORDERS.start();
        registry.add("payflow.jwt.secret", () -> "gateway-rate-limit-it-secret-32b");
        registry.add("ORDER_SERVICE_URI", () -> "http://localhost:" + ORDERS.getPort());
        registry.add("RATE_LIMIT_REPLENISH_RATE", () -> 1);
        registry.add("RATE_LIMIT_BURST_CAPACITY", () -> 1);
    }

    @AfterAll
    static void stopBackend() throws IOException {
        ORDERS.shutdown();
    }

    @Test
    void requestsOverTheLimitAreThrottled() {
        // Первый запрос забирает единственный токен ведра, остальные упираются в лимит.
        assertThat(login()).isEqualTo(HttpStatus.OK);

        assertThat(login()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private HttpStatus login() {
        return HttpStatus.valueOf(client.post()
                .uri("/api/v1/auth/login?probe=" + UUID.randomUUID())
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value());
    }
}
