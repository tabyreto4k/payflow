package ru.payflow.order.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.order.config.OutboxProperties;
import ru.payflow.order.logging.CorrelationId;
import ru.payflow.order.outbox.model.OutboxEvent;
import ru.payflow.order.outbox.repository.OutboxRepository;

@Component
public class OutboxPoller {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;

    public OutboxPoller(OutboxRepository outbox, KafkaTemplate<String, String> kafka, OutboxProperties properties) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
    }

    /**
     * Отправка ждётся синхронно и внутри транзакции: пометка {@code sent_at} доедет до базы только
     * после подтверждения брокером. Упавший или недоступный Kafka откатывает пачку целиком, записи
     * остаются неотправленными и уедут следующим проходом.
     *
     * <p>Ценой этого выбрана доставка at-least-once: если процесс умрёт между ack брокера и коммитом
     * транзакции, событие уйдёт повторно. Дубли разбирает потребитель по {@code eventId}.
     *
     * @return сколько событий отправлено — счётчик нужен тестам, чтобы не спать «на глазок»
     */
    @Transactional
    @Scheduled(
            fixedDelayString = "${payflow.outbox.poll-interval:PT0.2S}",
            initialDelayString = "${payflow.outbox.initial-delay:PT1S}")
    public int publishBatch() {
        List<OutboxEvent> batch = outbox.lockUnsentBatch(properties.batchSize());
        if (batch.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        for (OutboxEvent event : batch) {
            try {
                kafka.send(record(event)).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OutboxPublishException(event, e);
            } catch (Exception e) {
                throw new OutboxPublishException(event, e);
            }
            event.markSent(now);
        }

        LOG.info("Отправлено событий из outbox: {}", batch.size());
        return batch.size();
    }

    /**
     * Идентификатор запроса уезжает заголовком записи: у потребителя другого способа узнать, из
     * какого похода клиента выросло событие, нет — payload его не несёт и нести не должен.
     */
    private static ProducerRecord<String, String> record(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), null, event.getKey(), event.getPayload());
        if (event.getCorrelationId() != null) {
            record.headers().add(CorrelationId.HEADER, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
