package ru.payflow.payment.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.payment.account.dto.AccountResponse;
import ru.payflow.payment.account.dto.CreateAccountRequest;
import ru.payflow.payment.account.dto.DepositRequest;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.model.IdempotencyKey;
import ru.payflow.payment.account.repository.AccountRepository;
import ru.payflow.payment.account.repository.IdempotencyKeyRepository;
import ru.payflow.payment.cache.BalanceCache;
import ru.payflow.payment.exception.AccountAlreadyExistsException;
import ru.payflow.payment.exception.NotFoundException;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final BalanceCache cache;
    private final ObjectMapper json;

    public AccountService(
            AccountRepository accounts,
            IdempotencyKeyRepository idempotencyKeys,
            BalanceCache cache,
            ObjectMapper json) {
        this.accounts = accounts;
        this.idempotencyKeys = idempotencyKeys;
        this.cache = cache;
        this.json = json;
    }

    @Transactional
    public Account open(UUID customerId, CreateAccountRequest request) {
        if (accounts.existsByCustomerId(customerId)) {
            throw new AccountAlreadyExistsException(customerId);
        }
        return accounts.save(new Account(customerId, request.initialBalance()));
    }

    /**
     * Пополняет счёт ровно один раз на каждый {@code Idempotency-Key}: повтор возвращает тот же
     * ответ, что и первый запрос.
     */
    @Transactional
    public AccountResponse deposit(UUID customerId, UUID accountId, String idempotencyKey, DepositRequest request) {
        // Счёт блокируется раньше проверки ключа: два одновременных повтора выстраиваются
        // в очередь, и второй уже видит ключ, закоммиченный первым.
        Account account = accounts.findByIdForUpdate(accountId)
                .filter(owned(customerId))
                .orElseThrow(() -> new NotFoundException("Счёт %s не найден".formatted(accountId)));

        Optional<IdempotencyKey> replay = idempotencyKeys.findById(idempotencyKey);
        if (replay.isPresent()) {
            return readResponse(replay.get().getResponse());
        }

        account.deposit(request.amount());
        cache.evictAfterCommit(accountId);
        AccountResponse response = AccountResponse.from(account);
        idempotencyKeys.save(new IdempotencyKey(idempotencyKey, writeResponse(response)));
        return response;
    }

    /**
     * Чтение баланса идёт через кэш: владелец проверяется и на попадании — в кэше лежит ответ
     * целиком, вместе с {@code customerId}, поэтому чужой счёт остаётся невидимым.
     */
    @Transactional(readOnly = true)
    public AccountResponse getById(UUID customerId, UUID id) {
        Optional<AccountResponse> cached = cache.find(id);
        if (cached.isPresent()) {
            return cached.filter(response -> response.customerId().equals(customerId))
                    .orElseThrow(() -> new NotFoundException("Счёт %s не найден".formatted(id)));
        }

        AccountResponse response = accounts.findById(id)
                .filter(owned(customerId))
                .map(AccountResponse::from)
                .orElseThrow(() -> new NotFoundException("Счёт %s не найден".formatted(id)));
        cache.put(response);
        return response;
    }

    /** Чужой счёт отвечает 404, а не 403: существование чужих счетов клиента не касается. */
    private static Predicate<Account> owned(UUID customerId) {
        return account -> account.getCustomerId().equals(customerId);
    }

    private String writeResponse(AccountResponse response) {
        try {
            return json.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не сериализовать ответ для Idempotency-Key", e);
        }
    }

    private AccountResponse readResponse(String stored) {
        try {
            return json.readValue(stored, AccountResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не разобрать сохранённый ответ Idempotency-Key", e);
        }
    }
}
