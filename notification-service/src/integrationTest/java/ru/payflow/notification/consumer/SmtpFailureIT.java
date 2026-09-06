package ru.payflow.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.Topics;
import ru.payflow.notification.NotificationIT;

/**
 * SMTP недоступен. Письмо не уходит ни с одной попытки, сообщение уезжает в DLT — и отметка
 * идемпотентности снимается, иначе повтор из DLT приняли бы за дубль и письмо потерялось бы.
 */
@SpringBootTest
class SmtpFailureIT extends NotificationIT {

    private static final String KEY_PREFIX = "it-smtp-failure:event:";

    @DynamicPropertySource
    static void deadSmtp(DynamicPropertyRegistry registry) {
        // Порт, на котором никто не слушает: отказ приходит сразу, тест не ждёт таймаутов.
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> 1);
        // Контекст соседнего IT остаётся в кэше и слушает те же топики. Своя группа — чтобы
        // сообщения не делились между контекстами, свой префикс ключей — чтобы отметка этого
        // теста не выглядела для соседа обработанным событием (Redis у них общий: номер базы
        // задать нельзя, @ServiceConnection перебивает spring.data.redis.database).
        registry.add("spring.kafka.consumer.group-id", () -> "it-smtp-failure");
        registry.add("payflow.dedup.key-prefix", () -> KEY_PREFIX);
    }

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

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void undeliverableLetterEndsUpInTheDeadLetterTopic() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId, orderId, "it-" + UUID.randomUUID() + "@payflow.ru", new BigDecimal("40.00"), Instant.now());

        kafka.send(Topics.PAYMENTS_COMPLETED, orderId.toString(), json.writeValueAsString(event));

        ConsumerRecord<String, String> dead = awaitDeadLetter(orderId.toString());
        assertThat(dead.value()).contains(eventId.toString());
        assertThat(redis.hasKey(KEY_PREFIX + eventId)).isFalse();
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        // Один потребитель на весь цикл: новый на каждой итерации не успевал бы получить
        // назначение партиций и возвращал бы пусто, что бы в топике ни лежало.
        try (KafkaConsumer<String, String> consumer = deadLetterConsumer()) {
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("Сообщение с ключом " + key + " не доехало до DLT");
    }

    private KafkaConsumer<String, String> deadLetterConsumer() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(Topics.PAYMENTS_COMPLETED + ".DLT"));
        return consumer;
    }
}
