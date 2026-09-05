package ru.payflow.order.exception;

import java.util.UUID;

public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(UUID customerId) {
        super("У покупателя %s уже есть счёт".formatted(customerId));
    }
}
