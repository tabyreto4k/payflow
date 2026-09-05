package ru.payflow.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Деньги списаны. Публикует payment-service, слушают order-service и notification-service.
 *
 * @param eventId ключ идемпотентности: консьюмер отсеивает по нему повторную доставку
 */
public record PaymentCompletedEvent(UUID eventId, UUID orderId, Instant occurredAt) {}
