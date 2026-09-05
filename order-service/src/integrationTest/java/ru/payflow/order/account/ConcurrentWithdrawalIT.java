package ru.payflow.order.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.payflow.order.PostgresIT;
import ru.payflow.order.account.dto.CreateAccountRequest;
import ru.payflow.order.account.model.Account;
import ru.payflow.order.account.service.AccountService;

/**
 * Два списания по 70 со счёта в 100 стартуют одновременно. Без {@code SELECT ... FOR UPDATE} оба
 * прочитали бы баланс 100, оба сочли бы, что денег хватает, и счёт ушёл бы в −40. Повторов три:
 * гонка, которая ловится через раз, ничего не доказывает.
 */
@SpringBootTest
class ConcurrentWithdrawalIT extends PostgresIT {

    private static final BigDecimal INITIAL = new BigDecimal("100.00");
    private static final BigDecimal WITHDRAWAL = new BigDecimal("70.00");

    @Autowired
    private AccountService accounts;

    @RepeatedTest(3)
    void onlyOneOfTwoRacingWithdrawalsGoesThrough() throws Exception {
        UUID customerId = UUID.randomUUID();
        Account account = accounts.open(new CreateAccountRequest(customerId, INITIAL));

        List<Boolean> outcomes = raceTwoWithdrawals(customerId);

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(accounts.getById(account.getId()).getBalance()).isEqualByComparingTo("30.00");
    }

    private List<Boolean> raceTwoWithdrawals(UUID customerId) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> withdraw = () -> {
            start.await();
            return accounts.charge(customerId, WITHDRAWAL);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(withdraw);
            Future<Boolean> second = pool.submit(withdraw);
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
