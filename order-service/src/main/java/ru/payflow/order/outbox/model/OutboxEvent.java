package ru.payflow.order.outbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Событие, ждущее отправки в Kafka. Пишется в одной транзакции с изменением заказа: либо в базе
 * оба факта, либо ни одного — потерять событие после коммита бизнес-операции нельзя.
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    // Ключ партиции. Одинаковый ключ → одна партиция → события одного заказа не обгоняют друг друга.
    @Column(name = "key", nullable = false)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant sentAt;

    protected OutboxEvent() {}

    public OutboxEvent(String topic, String key, String payload) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Топик обязателен");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Ключ партиции обязателен");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload обязателен");
        }
        this.topic = topic;
        this.key = key;
        this.payload = payload;
    }

    public void markSent(Instant at) {
        if (sentAt != null) {
            throw new IllegalStateException("Событие " + id + " уже отправлено");
        }
        sentAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getKey() {
        return key;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
