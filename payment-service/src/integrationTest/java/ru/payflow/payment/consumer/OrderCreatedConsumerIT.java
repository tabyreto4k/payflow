package ru.payflow.payment.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.Topics;
import ru.payflow.payment.PostgresIT;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.repository.AccountRepository;
import ru.payflow.payment.outbox.model.OutboxEvent;
import ru.payflow.payment.outbox.repository.OutboxRepository;
import ru.payflow.payment.payment.model.Payment;
import ru.payflow.payment.payment.model.PaymentStatus;
import ru.payflow.payment.payment.repository.PaymentRepository;
import ru.payflow.payment.payment.service.PaymentService;

/**
 * Оплата по событию из Kafka и её идемпотентность. Дубли проверяются вызовом сервиса напрямую:
 * через брокер порядок доставки повторов не наблюдаем, и тест ловил бы гонку вместо логики.
 */
@SpringBootTest
class OrderCreatedConsumerIT extends PostgresIT {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        KAFKA.start();
    }

    /** Топик принадлежит order-service; здесь он объявлен, чтобы консьюмер нашёл его при старте. */
    @TestConfiguration
    static class InputTopic {

        @Bean
        public NewTopic ordersCreated() {
            return TopicBuilder.name(Topics.ORDERS_CREATED)
                    .partitions(3)
                    .replicas(1)
                    .build();
        }
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private PaymentService payments;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PaymentRepository paymentRows;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private ObjectMapper json;

    @Test
    void eventFromKafkaTakesMoneyAndPutsTheOutcomeIntoOutbox() throws Exception {
        UUID customer = openAccount("100.00");
        OrderCreatedEvent event = order(customer, "40.00");

        kafka.send(Topics.ORDERS_CREATED, event.orderId().toString(), json.writeValueAsString(event));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> paymentRows.findByOrderId(event.orderId()).isPresent());

        assertThat(balanceOf(customer)).isEqualByComparingTo("60.00");
        assertThat(paymentRows.findByOrderId(event.orderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);

        OutboxEvent outcome = outcomeFor(event.orderId());
        assertThat(outcome.getTopic()).isEqualTo(Topics.PAYMENTS_COMPLETED);
        assertThat(json.readValue(outcome.getPayload(), PaymentCompletedEvent.class)
                        .orderId())
                .isEqualTo(event.orderId());
    }

    @Test
    void theSameEventDeliveredTwiceChargesOnce() {
        UUID customer = openAccount("100.00");
        OrderCreatedEvent event = order(customer, "40.00");

        payments.processOrder(event);
        payments.processOrder(event);

        assertThat(balanceOf(customer)).isEqualByComparingTo("60.00");
        assertThat(paymentRows.findAll())
                .filteredOn(p -> p.getOrderId().equals(event.orderId()))
                .hasSize(1);
    }

    @Test
    void anotherEventAboutTheSameOrderChargesOnce() {
        UUID customer = openAccount("100.00");
        OrderCreatedEvent first = order(customer, "40.00");
        // Новый eventId: первый слой идемпотентности такое не ловит, ловит второй — по заказу.
        OrderCreatedEvent republished =
                new OrderCreatedEvent(UUID.randomUUID(), first.orderId(), customer, first.amount(), Instant.now());

        payments.processOrder(first);
        payments.processOrder(republished);

        assertThat(balanceOf(customer)).isEqualByComparingTo("60.00");
    }

    @Test
    void databaseRefusesASecondPaymentForTheSameOrder() {
        UUID order = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        paymentRows.saveAndFlush(Payment.completed(order, customer, new BigDecimal("10.00")));

        // Проверка в коде — не последний рубеж: уникальный индекс существует и работает.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(
                        () -> paymentRows.saveAndFlush(Payment.completed(order, customer, new BigDecimal("10.00"))));
    }

    @Test
    void unparsableMessageEndsUpInTheDeadLetterTopic() {
        String key = UUID.randomUUID().toString();

        kafka.send(Topics.ORDERS_CREATED, key, "{ это не событие }");

        // Бесконечный ретрай отравленной записи остановил бы всю партицию, а с ней и оплату
        // остальных заказов. Три попытки — и в DLT, разбирать руками.
        ConsumerRecord<String, String> dead = awaitDeadLetter(key);
        assertThat(dead.value()).isEqualTo("{ это не событие }");
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.ORDERS_CREATED + ".DLT"));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
            throw new AssertionError("Сообщение с ключом " + key + " не доехало до DLT");
        }
    }

    private UUID openAccount(String balance) {
        UUID customer = UUID.randomUUID();
        accounts.save(new Account(customer, new BigDecimal(balance)));
        return customer;
    }

    private BigDecimal balanceOf(UUID customer) {
        return accounts.findAll().stream()
                .filter(account -> account.getCustomerId().equals(customer))
                .findFirst()
                .orElseThrow()
                .getBalance();
    }

    private OutboxEvent outcomeFor(UUID orderId) {
        return outbox.findAll().stream()
                .filter(event -> event.getKey().equals(orderId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private static OrderCreatedEvent order(UUID customer, String amount) {
        return new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), customer, new BigDecimal(amount), Instant.now());
    }
}
