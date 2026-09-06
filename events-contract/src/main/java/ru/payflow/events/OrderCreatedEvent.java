package ru.payflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Заказ создан и ждёт оплаты. Публикует order-service, слушает payment-service.
 *
 * @param eventId ключ идемпотентности: консьюмер отсеивает по нему повторную доставку
 * @param customerEmail адрес для писем: кроме order-service его не знает никто, поэтому он едет
 *     с событием, а не вычитывается из чужой БД
 */
public record OrderCreatedEvent(
        UUID eventId, UUID orderId, UUID customerId, String customerEmail, BigDecimal amount, Instant occurredAt) {}
