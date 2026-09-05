package ru.payflow.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param batchSize сколько событий поллер забирает за один проход
 */
@ConfigurationProperties(prefix = "payflow.outbox")
public record OutboxProperties(int batchSize) {

    public OutboxProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("payflow.outbox.batch-size должен быть положительным");
        }
    }
}
