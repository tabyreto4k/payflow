package ru.payflow.payment.outbox;

import ru.payflow.payment.outbox.model.OutboxEvent;

/** Событие не доехало до брокера. Откатывает пачку — записи останутся неотправленными. */
public class OutboxPublishException extends RuntimeException {

    public OutboxPublishException(OutboxEvent event, Throwable cause) {
        super("Не отправлено событие " + event.getId() + " в топик " + event.getTopic(), cause);
    }
}
