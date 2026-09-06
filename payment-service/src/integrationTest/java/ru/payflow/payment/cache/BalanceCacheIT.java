package ru.payflow.payment.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.payment.PaymentIT;
import ru.payflow.payment.account.dto.CreateAccountRequest;
import ru.payflow.payment.account.dto.DepositRequest;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.service.AccountService;
import ru.payflow.payment.payment.service.PaymentService;

/**
 * Кэш баланса не должен переживать изменение счёта. Проверяются оба пути записи: пополнение через
 * API и списание сагой, которое идёт мимо {@link AccountService}.
 */
@SpringBootTest
class BalanceCacheIT extends PaymentIT {

    private final UUID customer = UUID.randomUUID();

    @Autowired
    private AccountService accounts;

    @Autowired
    private PaymentService payments;

    @Autowired
    private BalanceCache cache;

    @Test
    void readPutsTheAccountIntoTheCache() {
        UUID accountId = openAccount("100.00");

        assertThat(cache.find(accountId)).isEmpty();
        accounts.getById(customer, accountId);

        assertThat(cache.find(accountId)).isPresent();
        assertThat(cache.find(accountId).orElseThrow().balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void depositInvalidatesTheCache() {
        UUID accountId = openAccount("100.00");
        accounts.getById(customer, accountId);

        accounts.deposit(
                customer, accountId, UUID.randomUUID().toString(), new DepositRequest(new BigDecimal("50.00")));

        // Без инвалидации здесь читалась бы сотня: запись в кэше пережила бы пополнение.
        assertThat(cache.find(accountId)).isEmpty();
        assertThat(accounts.getById(customer, accountId).balance()).isEqualByComparingTo("150.00");
    }

    @Test
    void sagaWithdrawalInvalidatesTheCacheToo() {
        UUID accountId = openAccount("100.00");
        accounts.getById(customer, accountId);

        payments.processOrder(new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                customer,
                "it@payflow.ru",
                new BigDecimal("40.00"),
                Instant.now()));

        assertThat(cache.find(accountId)).isEmpty();
        assertThat(accounts.getById(customer, accountId).balance()).isEqualByComparingTo("60.00");
    }

    private UUID openAccount(String balance) {
        Account account = accounts.open(customer, new CreateAccountRequest(new BigDecimal(balance)));
        return account.getId();
    }
}
