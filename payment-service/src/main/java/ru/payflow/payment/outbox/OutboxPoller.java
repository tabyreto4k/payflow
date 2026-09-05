package ru.payflow.payment.outbox;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.payment.config.OutboxProperties;
import ru.payflow.payment.outbox.model.OutboxEvent;
import ru.payflow.payment.outbox.repository.OutboxRepository;

/**
 * Копия поллера из order-service, а не общий модуль: общий модуль здесь ровно один и это
 * {@code events-contract} (ТЗ п.4.10). Вынести outbox в библиотеку — значит связать сервисы
 * ещё и расписанием и схемой таблицы; дешевле повторить сорок строк.
 */
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
                kafka.send(event.getTopic(), event.getKey(), event.getPayload()).get();
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
}
