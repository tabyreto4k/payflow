package ru.payflow.payment.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.payflow.payment.account.dto.CreateAccountRequest;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.repository.AccountRepository;
import ru.payflow.payment.exception.AccountAlreadyExistsException;
import ru.payflow.payment.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accounts;

    @InjectMocks
    private AccountService service;

    @Test
    void opensAccountForNewCustomer() {
        UUID customerId = UUID.randomUUID();
        when(accounts.existsByCustomerId(customerId)).thenReturn(false);
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account account = service.open(customerId, new CreateAccountRequest(new BigDecimal("100.00")));

        assertThat(account.getCustomerId()).isEqualTo(customerId);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void refusesSecondAccountForSameCustomer() {
        UUID customerId = UUID.randomUUID();
        when(accounts.existsByCustomerId(customerId)).thenReturn(true);

        assertThatExceptionOfType(AccountAlreadyExistsException.class)
                .isThrownBy(() -> service.open(customerId, new CreateAccountRequest(BigDecimal.TEN)));
        verify(accounts, never()).save(any());
    }

    @Test
    void readsOwnAccountById() {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Account stored = new Account(customerId, BigDecimal.TEN);
        when(accounts.findById(id)).thenReturn(Optional.of(stored));

        assertThat(service.getById(customerId, id)).isSameAs(stored);
    }

    @Test
    void unknownIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.getById(UUID.randomUUID(), id));
    }

    @Test
    void strangersAccountIsNotFound() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.of(new Account(UUID.randomUUID(), BigDecimal.TEN)));

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.getById(UUID.randomUUID(), id));
    }
}
