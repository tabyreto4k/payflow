package ru.payflow.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.Topics;
import ru.payflow.payment.payment.service.PaymentService;

@Component
public class OrderCreatedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final PaymentService payments;
    private final ObjectMapper json;

    public OrderCreatedConsumer(PaymentService payments, ObjectMapper json) {
        this.payments = payments;
        this.json = json;
    }

    /**
     * Разбор и обработка разделены намеренно: неразбираемое сообщение — это отравленная запись,
     * её ретраить бессмысленно, она уедет в DLT. Ошибка обработки — другое дело, её ретрай лечит.
     */
    @KafkaListener(topics = Topics.ORDERS_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCreated(String payload) throws Exception {
        OrderCreatedEvent event = json.readValue(payload, OrderCreatedEvent.class);
        LOG.info("Оплата заказа {} по событию {}", event.orderId(), event.eventId());
        payments.processOrder(event);
    }
}
