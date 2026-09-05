package ru.payflow.order.exception;

import ru.payflow.order.order.model.OrderStatus;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Переход %s → %s запрещён".formatted(from, to));
    }
}
