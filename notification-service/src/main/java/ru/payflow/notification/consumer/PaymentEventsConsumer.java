package ru.payflow.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
import ru.payflow.notification.idempotency.EventDeduplicator;
import ru.payflow.notification.logging.CorrelationId;
import ru.payflow.notification.service.NotificationService;

/** Исходы оплаты слушаются напрямую, своего топика у уведомлений нет [Р5]. */
@Component
public class PaymentEventsConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentEventsConsumer.class);

    private final NotificationService notifications;
    private final EventDeduplicator deduplicator;
    private final ObjectMapper json;

    public PaymentEventsConsumer(NotificationService notifications, EventDeduplicator deduplicator, ObjectMapper json) {
        this.notifications = notifications;
        this.deduplicator = deduplicator;
        this.json = json;
    }

    @KafkaListener(topics = Topics.PAYMENTS_COMPLETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentCompleted(String payload, @Header(name = CorrelationId.HEADER, required = false) byte[] id)
            throws Exception {
        PaymentCompletedEvent event = json.readValue(payload, PaymentCompletedEvent.class);
        CorrelationId.with(decode(id), () -> once(event.eventId(), () -> notifications.notifyPaid(event)));
    }

    @KafkaListener(topics = Topics.PAYMENTS_FAILED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(String payload, @Header(name = CorrelationId.HEADER, required = false) byte[] id)
            throws Exception {
        PaymentFailedEvent event = json.readValue(payload, PaymentFailedEvent.class);
        CorrelationId.with(decode(id), () -> once(event.eventId(), () -> notifications.notifyFailed(event)));
    }

    /**
     * Заголовок приезжает сырыми байтами: типа в нём нет, и маппер Spring Kafka строкой его не
     * отдаст. Декодируем сами — это дешевле, чем настраивать доверенные типы заголовков.
     */
    private static String decode(byte[] header) {
        return header == null ? null : new String(header, StandardCharsets.UTF_8);
    }

    /**
     * Отметка ставится до отправки, а не после: дубль, приехавший пока письмо в пути, иначе привёл
     * бы ко второму письму. Если отправка сорвалась — отметка снимается, иначе повтор из Kafka
     * приняли бы за дубль и уведомление потерялось бы молча.
     */
    private void once(UUID eventId, Runnable send) {
        if (!deduplicator.tryMarkProcessed(eventId)) {
            LOG.info("Событие {} уже обработано, письмо не дублируем", eventId);
            return;
        }
        try {
            send.run();
        } catch (RuntimeException e) {
            deduplicator.forget(eventId);
            throw e;
        }
    }
}
