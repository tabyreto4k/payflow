package ru.payflow.payment.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import ru.payflow.payment.exception.InsufficientFundsException;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true, updatable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {}

    public Account(UUID customerId, BigDecimal initialBalance) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId обязателен");
        }
        if (initialBalance == null || initialBalance.signum() < 0) {
            throw new IllegalArgumentException("Начальный баланс не может быть отрицательным: " + initialBalance);
        }
        this.customerId = customerId;
        this.balance = initialBalance;
    }

    public void deposit(BigDecimal amount) {
        balance = balance.add(requirePositive(amount));
    }

    public void withdraw(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(id, amount);
        }
        balance = balance.subtract(amount);
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Сумма операции должна быть положительной: " + amount);
        }
        return amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
