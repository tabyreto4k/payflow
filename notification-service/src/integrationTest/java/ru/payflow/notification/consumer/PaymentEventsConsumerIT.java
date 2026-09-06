package ru.payflow.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
import ru.payflow.notification.NotificationIT;

/** Событие оплаты доезжает до письма в MailHog, а его дубль второго письма не порождает. */
@SpringBootTest
class PaymentEventsConsumerIT extends NotificationIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @DynamicPropertySource
    static void mailhog(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILHOG::getHost);
        registry.add("spring.mail.port", () -> MAILHOG.getMappedPort(1025));
        // Своя группа: контекст SmtpFailureIT остаётся в кэше и слушает те же топики, а в общей
        // группе они делили бы партиции и половина сообщений уходила бы не туда.
        registry.add("spring.kafka.consumer.group-id", () -> "it-letters");
    }

    /** Топики исходов принадлежат payment-service; здесь они нужны консьюмеру при старте. */
    @TestConfiguration
    static class OutcomeTopics {

        @Bean
        public NewTopic paymentsCompleted() {
            return TopicBuilder.name(Topics.PAYMENTS_COMPLETED)
                    .partitions(3)
                    .replicas(1)
                    .build();
        }

        @Bean
        public NewTopic paymentsFailed() {
            return TopicBuilder.name(Topics.PAYMENTS_FAILED)
                    .partitions(3)
                    .replicas(1)
                    .build();
        }
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void completedPaymentBecomesALetter() throws Exception {
        String customer = customer();
        UUID orderId = UUID.randomUUID();

        send(Topics.PAYMENTS_COMPLETED, orderId, completed(UUID.randomUUID(), orderId, customer));

        await().atMost(TIMEOUT).until(() -> lettersTo(customer) == 1);
    }

    @Test
    void failedPaymentBecomesALetterToo() throws Exception {
        String customer = customer();
        UUID orderId = UUID.randomUUID();

        send(
                Topics.PAYMENTS_FAILED,
                orderId,
                new PaymentFailedEvent(
                        UUID.randomUUID(),
                        orderId,
                        customer,
                        new BigDecimal("1000.00"),
                        "insufficient_funds",
                        Instant.now()));

        await().atMost(TIMEOUT).until(() -> lettersTo(customer) == 1);
    }

    @Test
    void duplicateEventSendsOneLetter() throws Exception {
        String customer = customer();
        UUID orderId = UUID.randomUUID();
        PaymentCompletedEvent event = completed(UUID.randomUUID(), orderId, customer);

        send(Topics.PAYMENTS_COMPLETED, orderId, event);
        send(Topics.PAYMENTS_COMPLETED, orderId, event);
        await().atMost(TIMEOUT).until(() -> lettersTo(customer) == 1);

        // Событие с тем же ключом уходит в ту же партицию и обрабатывается строго после дубля:
        // дождавшись его письма, мы знаем, что дубль уже прожёван, а не «наверное, успел».
        String barrier = customer();
        send(Topics.PAYMENTS_COMPLETED, orderId, completed(UUID.randomUUID(), orderId, barrier));
        await().atMost(TIMEOUT).until(() -> lettersTo(barrier) == 1);

        assertThat(lettersTo(customer)).isEqualTo(1);
    }

    private static PaymentCompletedEvent completed(UUID eventId, UUID orderId, String customer) {
        return new PaymentCompletedEvent(eventId, orderId, customer, new BigDecimal("40.00"), Instant.now());
    }

    /** Свой адрес на каждый тест: MailHog копит письма на всю JVM, чистить его между тестами негде. */
    private static String customer() {
        return "it-" + UUID.randomUUID() + "@payflow.ru";
    }

    private void send(String topic, UUID orderId, Object event) throws Exception {
        kafka.send(topic, orderId.toString(), json.writeValueAsString(event));
    }

    private long lettersTo(String address) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailhogApi() + "/api/v2/messages?limit=200"))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode items = json.readTree(response.body()).path("items");
        return items.valueStream().filter(item -> addressed(item, address)).count();
    }

    /** MailHog хранит адрес разобранным на части, целой строки в ответе нет. */
    private static boolean addressed(JsonNode message, String address) {
        return message.path("To")
                .valueStream()
                .anyMatch(to -> address.equals(
                        to.path("Mailbox").asText() + "@" + to.path("Domain").asText()));
    }
}
