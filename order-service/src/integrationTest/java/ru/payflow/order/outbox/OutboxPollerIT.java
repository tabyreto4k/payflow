package ru.payflow.order.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.testcontainers.kafka.KafkaContainer;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.Topics;
import ru.payflow.order.PostgresIT;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderItemRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.service.OrderService;
import ru.payflow.order.outbox.model.OutboxEvent;
import ru.payflow.order.outbox.repository.OutboxRepository;

/**
 * Событие переживает недоступность брокера. Проверяется то, ради чего outbox и заведён: пока запись
 * не подтверждена Kafka, она остаётся в таблице и уезжает следующим проходом.
 */
@SpringBootTest
class OutboxPollerIT extends PostgresIT {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        KAFKA.start();
    }

    @Autowired
    private OrderService orders;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private OutboxPoller poller;

    @Autowired
    private ProducerFactory<String, String> producerFactory;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void clearOutbox() {
        outbox.deleteAll();
    }

    @Test
    void createdOrderReachesKafkaAndTheRowIsMarked() throws Exception {
        OrderResponse order = createOrder("40.00");
        assertThat(unsent()).hasSize(1);

        assertThat(poller.publishBatch()).isEqualTo(1);

        List<ConsumerRecord<String, String>> delivered = recordsFor(order.id());
        assertThat(delivered).singleElement();
        OrderCreatedEvent event = json.readValue(delivered.getFirst().value(), OrderCreatedEvent.class);
        assertThat(event.orderId()).isEqualTo(order.id());
        assertThat(event.amount()).isEqualByComparingTo("40.00");

        assertThat(unsent()).isEmpty();
        assertThat(outbox.findAll()).singleElement().satisfies(row -> assertThat(row.getSentAt())
                .isNotNull());
    }

    @Test
    void pausedBrokerLeavesTheRowForTheNextRound() throws Exception {
        OrderResponse order = createOrder("15.00");

        pauseKafka();
        try {
            assertThatExceptionOfType(OutboxPublishException.class).isThrownBy(() -> poller.publishBatch());
            // Главное утверждение теста: отказ брокера не съел событие.
            assertThat(unsent()).hasSize(1);
        } finally {
            resumeKafka();
        }

        assertThat(poller.publishBatch()).isEqualTo(1);
        assertThat(unsent()).isEmpty();

        // Не «ровно одна»: приостановленный брокер мог успеть записать первую попытку до того, как
        // у продюсера истекла доставка. Это и есть обещанная at-least-once — дубль разбирает
        // потребитель по eventId, а не отправитель.
        assertThat(recordsFor(order.id())).isNotEmpty();
    }

    private OrderResponse createOrder(String price) {
        return orders.create(
                UUID.randomUUID(),
                new CreateOrderRequest(List.of(new OrderItemRequest(UUID.randomUUID(), 1, new BigDecimal(price)))));
    }

    private List<OutboxEvent> unsent() {
        return outbox.findAll().stream()
                .filter(event -> event.getSentAt() == null)
                .toList();
    }

    private static void pauseKafka() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private void resumeKafka() {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        // Продюсер идемпотентный: истёкшая доставка оставляет его со сбитой последовательностью.
        // Пересоздание — то же, что перезапуск сервиса, только без перезапуска контекста.
        ((DefaultKafkaProducerFactory<String, String>) producerFactory).reset();
    }

    private List<ConsumerRecord<String, String>> recordsFor(UUID orderId) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.ORDERS_CREATED));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            List<ConsumerRecord<String, String>> mine = new ArrayList<>();
            records.forEach(record -> {
                if (record.key().equals(orderId.toString())) {
                    mine.add(record);
                }
            });
            return mine;
        }
    }
}
