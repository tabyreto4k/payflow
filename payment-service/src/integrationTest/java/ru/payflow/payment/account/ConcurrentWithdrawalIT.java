package ru.payflow.payment.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
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
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.payment.PaymentIT;
import ru.payflow.payment.account.dto.CreateAccountRequest;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.service.AccountService;
import ru.payflow.payment.payment.model.PaymentStatus;
import ru.payflow.payment.payment.repository.PaymentRepository;
import ru.payflow.payment.payment.service.PaymentService;

/**
 * Два заказа по 70 на счёт в 100 обрабатываются одновременно. Без {@code SELECT ... FOR UPDATE}
 * оба прочитали бы баланс 100, оба сочли бы, что денег хватает, и счёт ушёл бы в −40. Повторов
 * три: гонка, которая ловится через раз, ничего не доказывает.
 *
 * <p>Гонка гоняется по настоящему пути саги — через {@link PaymentService}: списание живёт там,
 * и проверять надо его, а не метод, заведённый ради теста.
 */
@SpringBootTest
class ConcurrentWithdrawalIT extends PaymentIT {

    private static final BigDecimal INITIAL = new BigDecimal("100.00");
    private static final BigDecimal ORDER_AMOUNT = new BigDecimal("70.00");

    @Autowired
    private AccountService accounts;

    @Autowired
    private PaymentService payments;

    @Autowired
    private PaymentRepository paymentRows;

    @RepeatedTest(3)
    void onlyOneOfTwoRacingOrdersIsPaid() throws Exception {
        UUID customerId = UUID.randomUUID();
        Account account = accounts.open(customerId, new CreateAccountRequest(INITIAL));
        UUID firstOrder = UUID.randomUUID();
        UUID secondOrder = UUID.randomUUID();

        race(customerId, firstOrder, secondOrder);

        assertThat(List.of(statusOf(firstOrder), statusOf(secondOrder)))
                .containsExactlyInAnyOrder(PaymentStatus.COMPLETED, PaymentStatus.FAILED);
        assertThat(accounts.getById(customerId, account.getId()).balance()).isEqualByComparingTo("30.00");
    }

    private void race(UUID customerId, UUID firstOrder, UUID secondOrder) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(process(start, customerId, firstOrder));
            Future<?> second = pool.submit(process(start, customerId, secondOrder));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Void> process(CountDownLatch start, UUID customerId, UUID orderId) {
        return () -> {
            start.await();
            payments.processOrder(new OrderCreatedEvent(
                    UUID.randomUUID(), orderId, customerId, "it@payflow.ru", ORDER_AMOUNT, Instant.now()));
            return null;
        };
    }

    private PaymentStatus statusOf(UUID orderId) {
        return paymentRows.findByOrderId(orderId).orElseThrow().getStatus();
    }
}
