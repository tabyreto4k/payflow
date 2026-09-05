package ru.payflow.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Оплата не прошла — заказу отменяться. Публикует payment-service, слушают order- и
 * notification-service.
 *
 * @param eventId ключ идемпотентности: консьюмер отсеивает по нему повторную доставку
 * @param reason машиночитаемая причина, например {@code insufficient_funds}
 */
public record PaymentFailedEvent(UUID eventId, UUID orderId, String reason, Instant occurredAt) {}
