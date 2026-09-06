package ru.payflow.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.Topics;
import ru.payflow.payment.logging.CorrelationId;
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
    public void onOrderCreated(String payload, @Header(name = CorrelationId.HEADER, required = false) byte[] id)
            throws Exception {
        OrderCreatedEvent event = json.readValue(payload, OrderCreatedEvent.class);
        CorrelationId.with(decode(id), () -> {
            LOG.info("Оплата заказа {} по событию {}", event.orderId(), event.eventId());
            payments.processOrder(event);
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
