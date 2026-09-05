package ru.payflow.order.account.dto;

import java.math.BigDecimal;
import java.util.UUID;
import ru.payflow.order.account.model.Account;

public record AccountResponse(UUID id, UUID customerId, BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getCustomerId(), account.getBalance());
    }
}
