package ru.payflow.order.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    private final UUID accountId;
    private final BigDecimal requested;

    public InsufficientFundsException(UUID accountId, BigDecimal requested) {
        super("Недостаточно средств на счёте %s для списания %s".formatted(accountId, requested));
        this.accountId = accountId;
        this.requested = requested;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getRequested() {
        return requested;
    }
}
