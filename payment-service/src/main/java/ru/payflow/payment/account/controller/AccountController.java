package ru.payflow.payment.account.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.payflow.payment.account.dto.AccountResponse;
import ru.payflow.payment.account.dto.CreateAccountRequest;
import ru.payflow.payment.account.dto.DepositRequest;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.service.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(
            @AuthenticationPrincipal UUID customerId, @Valid @RequestBody CreateAccountRequest request) {
        Account account = accounts.open(customerId, request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @PostMapping("/{id}/deposit")
    public AccountResponse deposit(
            @AuthenticationPrincipal UUID customerId,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        return accounts.deposit(customerId, id, idempotencyKey, request);
    }

    @GetMapping("/{id}")
    public AccountResponse getById(@AuthenticationPrincipal UUID customerId, @PathVariable UUID id) {
        return AccountResponse.from(accounts.getById(customerId, id));
    }
}
