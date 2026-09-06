package ru.payflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Деньги списаны. Публикует payment-service, слушают order-service и notification-service.
 *
 * @param eventId ключ идемпотентности: консьюмер отсеивает по нему повторную доставку
 * @param customerEmail адресат письма, приезжает из {@link OrderCreatedEvent}
 * @param amount сколько списано — письму нужна сумма, а второй раз считать её негде
 */
public record PaymentCompletedEvent(
        UUID eventId, UUID orderId, String customerEmail, BigDecimal amount, Instant occurredAt) {}
