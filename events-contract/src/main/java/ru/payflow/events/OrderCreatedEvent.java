package ru.payflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Заказ создан и ждёт оплаты. Публикует order-service, слушает payment-service.
 *
 * @param eventId ключ идемпотентности: консьюмер отсеивает по нему повторную доставку
 */
public record OrderCreatedEvent(UUID eventId, UUID orderId, UUID customerId, BigDecimal amount, Instant occurredAt) {}
