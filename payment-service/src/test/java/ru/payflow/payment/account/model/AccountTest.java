package ru.payflow.payment.account.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.payflow.payment.exception.InsufficientFundsException;

class AccountTest {

    private static final UUID CUSTOMER = UUID.randomUUID();

    @Test
    void keepsInitialBalance() {
        Account account = new Account(CUSTOMER, new BigDecimal("100.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        assertThat(account.getCustomerId()).isEqualTo(CUSTOMER);
    }

    @Test
    void opensWithZeroBalance() {
        assertThat(new Account(CUSTOMER, BigDecimal.ZERO).getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsNegativeInitialBalance() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Account(CUSTOMER, new BigDecimal("-0.01")));
    }

    @Test
    void rejectsMissingCustomer() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Account(null, BigDecimal.TEN));
    }

    @Test
    void depositAddsToBalance() {
        Account account = new Account(CUSTOMER, new BigDecimal("100.00"));

        account.deposit(new BigDecimal("40.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("140.50");
    }

    @Test
    void depositRejectsNonPositiveAmount() {
        Account account = new Account(CUSTOMER, new BigDecimal("100.00"));

        assertThatIllegalArgumentException().isThrownBy(() -> account.deposit(new BigDecimal("-1.00")));
        assertThatIllegalArgumentException().isThrownBy(() -> account.deposit(BigDecimal.ZERO));
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawSubtractsFromBalance() {
        Account account = new Account(CUSTOMER, new BigDecimal("100.00"));

        account.withdraw(new BigDecimal("40.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawEmptiesBalanceExactly() {
        Account account = new Account(CUSTOMER, new BigDecimal("100.00"));

        account.withdraw(new BigDecimal("100.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawBeyondBalanceLeavesItUntouched() {
        Account account = new Account(CUSTOMER, new BigDecimal("100.00"));

        assertThatExceptionOfType(InsufficientFundsException.class)
                .isThrownBy(() -> account.withdraw(new BigDecimal("100.01")));
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawRejectsNonPositiveAmount() {
        Account account = new Account(CUSTOMER, new BigDecimal("100.00"));

        assertThatIllegalArgumentException().isThrownBy(() -> account.withdraw(BigDecimal.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> account.withdraw(new BigDecimal("-1.00")));
    }
}
