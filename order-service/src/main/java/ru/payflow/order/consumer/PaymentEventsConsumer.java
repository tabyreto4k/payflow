package ru.payflow.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
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
    public void onPaymentCompleted(String payload) throws Exception {
        PaymentCompletedEvent event = json.readValue(payload, PaymentCompletedEvent.class);
        LOG.info("Заказ {} оплачен, событие {}", event.orderId(), event.eventId());
        orders.applyPaid(event);
    }

    @KafkaListener(topics = Topics.PAYMENTS_FAILED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(String payload) throws Exception {
        PaymentFailedEvent event = json.readValue(payload, PaymentFailedEvent.class);
        LOG.info("Оплата заказа {} не прошла ({}), событие {}", event.orderId(), event.reason(), event.eventId());
        orders.applyFailed(event);
    }
}
