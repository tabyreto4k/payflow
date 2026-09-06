package ru.payflow.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
import ru.payflow.order.logging.CorrelationId;
import ru.payflow.order.order.service.OrderService;

/** Исходы оплаты, замыкающие сагу: они и двигают заказ из {@code AWAITING_PAYMENT}. */
@Component
public class PaymentEventsConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentEventsConsumer.class);

    private final OrderService orders;
    private final ObjectMapper json;

    public PaymentEventsConsumer(OrderService orders, ObjectMapper json) {
        this.orders = orders;
        this.json = json;
    }

    @KafkaListener(topics = Topics.PAYMENTS_COMPLETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentCompleted(String payload, @Header(name = CorrelationId.HEADER, required = false) byte[] id)
            throws Exception {
        PaymentCompletedEvent event = json.readValue(payload, PaymentCompletedEvent.class);
        CorrelationId.with(decode(id), () -> {
            LOG.info("Заказ {} оплачен, событие {}", event.orderId(), event.eventId());
            orders.applyPaid(event);
        });
    }

    @KafkaListener(topics = Topics.PAYMENTS_FAILED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(String payload, @Header(name = CorrelationId.HEADER, required = false) byte[] id)
            throws Exception {
        PaymentFailedEvent event = json.readValue(payload, PaymentFailedEvent.class);
        CorrelationId.with(decode(id), () -> {
            LOG.info("Оплата заказа {} не прошла ({}), событие {}", event.orderId(), event.reason(), event.eventId());
            orders.applyFailed(event);
        });
    }

    /**
     * Заголовок приезжает сырыми байтами: типа в нём нет, и маппер Spring Kafka строкой его не
     * отдаст. Декодируем сами — это дешевле, чем настраивать доверенные типы заголовков.
     */
    private static String decode(byte[] header) {
        return header == null ? null : new String(header, StandardCharsets.UTF_8);
    }
}
