package ru.payflow.payment.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Страховка инварианта счёта, а не ветка бизнес-логики: списание сначала спрашивает, хватает ли
 * денег, и на нехватку отвечает {@code false}. Если исключение всё-таки вылетело — разошлись
 * проверка и инвариант, это баг, а не ответ клиенту.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, BigDecimal requested) {
        super("Недостаточно средств на счёте %s для списания %s".formatted(accountId, requested));
    }
}
