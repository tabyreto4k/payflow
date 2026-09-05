package ru.payflow.payment.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Исход оплаты заказа. Ровно один на заказ — это держит уникальный индекс по {@code order_id}. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private PaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {}

    private Payment(UUID orderId, UUID customerId, BigDecimal amount, PaymentStatus status) {
        if (orderId == null || customerId == null) {
            throw new IllegalArgumentException("orderId и customerId обязательны");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Сумма оплаты должна быть положительной: " + amount);
        }
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    public static Payment completed(UUID orderId, UUID customerId, BigDecimal amount) {
        return new Payment(orderId, customerId, amount, PaymentStatus.COMPLETED);
    }

    public static Payment failed(UUID orderId, UUID customerId, BigDecimal amount) {
        return new Payment(orderId, customerId, amount, PaymentStatus.FAILED);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
