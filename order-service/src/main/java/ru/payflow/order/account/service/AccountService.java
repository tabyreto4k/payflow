package ru.payflow.order.account.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.order.account.dto.CreateAccountRequest;
import ru.payflow.order.account.model.Account;
import ru.payflow.order.account.repository.AccountRepository;
import ru.payflow.order.exception.AccountAlreadyExistsException;
import ru.payflow.order.exception.NotFoundException;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account open(CreateAccountRequest request) {
        if (accounts.existsByCustomerId(request.customerId())) {
            throw new AccountAlreadyExistsException(request.customerId());
        }
        return accounts.save(new Account(request.customerId(), request.initialBalance()));
    }

    @Transactional(readOnly = true)
    public Account getById(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("Счёт %s не найден".formatted(id)));
    }
}
