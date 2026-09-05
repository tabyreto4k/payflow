package ru.payflow.order.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
import ru.payflow.order.PostgresIT;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderItemRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.OrderStatus;
import ru.payflow.order.order.repository.OrderRepository;
import ru.payflow.order.order.service.OrderService;

/** Замыкание саги: исход оплаты приходит из Kafka и двигает заказ из AWAITING_PAYMENT. */
@SpringBootTest
class PaymentEventsConsumerIT extends PostgresIT {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        KAFKA.start();
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
    private OrderService orders;

    @Autowired
    private OrderRepository orderRows;

    @Autowired
    private ObjectMapper json;

    @Test
    void completedPaymentMovesTheOrderToPaid() throws Exception {
        UUID orderId = createOrder();

        send(Topics.PAYMENTS_COMPLETED, orderId, new PaymentCompletedEvent(UUID.randomUUID(), orderId, Instant.now()));

        awaitStatus(orderId, OrderStatus.PAID);
    }

    @Test
    void failedPaymentCancelsTheOrder() throws Exception {
        UUID orderId = createOrder();

        send(
                Topics.PAYMENTS_FAILED,
                orderId,
                new PaymentFailedEvent(UUID.randomUUID(), orderId, "insufficient_funds", Instant.now()));

        awaitStatus(orderId, OrderStatus.CANCELLED);
    }

    @Test
    void duplicateOutcomeMovesTheStatusOnceAndDoesNotPoisonThePartition() throws Exception {
        UUID orderId = createOrder();
        PaymentCompletedEvent event = new PaymentCompletedEvent(UUID.randomUUID(), orderId, Instant.now());

        send(Topics.PAYMENTS_COMPLETED, orderId, event);
        send(Topics.PAYMENTS_COMPLETED, orderId, event);
        awaitStatus(orderId, OrderStatus.PAID);

        // Заказ с тем же ключом уходит в ту же партицию и обрабатывается строго после дубля:
        // дождавшись его, мы знаем, что дубль уже прожёван, а не «наверное, успел».
        UUID barrier = createOrder();
        send(
                Topics.PAYMENTS_COMPLETED,
                barrier,
                new PaymentCompletedEvent(UUID.randomUUID(), barrier, Instant.now()),
                orderId);
        awaitStatus(barrier, OrderStatus.PAID);

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
        assertThat(deadLettersFor(orderId)).isEmpty();
    }

    @Test
    void unparsableOutcomeEndsUpInTheDeadLetterTopic() {
        String key = UUID.randomUUID().toString();

        kafka.send(Topics.PAYMENTS_COMPLETED, key, "{ это не событие }");

        ConsumerRecord<String, String> dead = awaitDeadLetter(key);
        assertThat(dead.value()).isEqualTo("{ это не событие }");
    }

    private UUID createOrder() {
        OrderResponse response = orders.create(
                UUID.randomUUID(),
                new CreateOrderRequest(List.of(new OrderItemRequest(UUID.randomUUID(), 1, new BigDecimal("10.00")))));
        return response.id();
    }

    private void send(String topic, UUID orderId, Object event) throws Exception {
        send(topic, orderId, event, orderId);
    }

    private void send(String topic, UUID orderId, Object event, UUID partitionKey) throws Exception {
        kafka.send(topic, partitionKey.toString(), json.writeValueAsString(event));
    }

    private void awaitStatus(UUID orderId, OrderStatus expected) {
        await().atMost(Duration.ofSeconds(30)).until(() -> statusOf(orderId) == expected);
    }

    private OrderStatus statusOf(UUID orderId) {
        return orderRows.findById(orderId).orElseThrow().getStatus();
    }

    /**
     * Без отсечки по eventId дубль упал бы на переходе PAID → PAID и уехал бы сюда. Пустая DLT —
     * и есть доказательство, что идемпотентность работает.
     */
    private List<ConsumerRecord<String, String>> deadLettersFor(UUID orderId) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = deadLetterConsumer()) {
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofSeconds(1)).forEach(record -> {
                    if (record.key().equals(orderId.toString())) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        // Один потребитель на весь цикл: новый на каждой итерации не успевал бы получить
        // назначение партиций и возвращал бы пусто, что бы в топике ни лежало.
        try (KafkaConsumer<String, String> consumer = deadLetterConsumer()) {
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
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
