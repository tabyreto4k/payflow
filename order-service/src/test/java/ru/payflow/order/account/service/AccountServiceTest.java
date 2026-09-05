package ru.payflow.order.account.service;

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
import ru.payflow.order.account.dto.CreateAccountRequest;
import ru.payflow.order.account.model.Account;
import ru.payflow.order.account.repository.AccountRepository;
import ru.payflow.order.exception.AccountAlreadyExistsException;
import ru.payflow.order.exception.NotFoundException;

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

        Account account = service.open(new CreateAccountRequest(customerId, new BigDecimal("100.00")));

        assertThat(account.getCustomerId()).isEqualTo(customerId);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void refusesSecondAccountForSameCustomer() {
        UUID customerId = UUID.randomUUID();
        when(accounts.existsByCustomerId(customerId)).thenReturn(true);

        assertThatExceptionOfType(AccountAlreadyExistsException.class)
                .isThrownBy(() -> service.open(new CreateAccountRequest(customerId, BigDecimal.TEN)));
        verify(accounts, never()).save(any());
    }

    @Test
    void readsAccountById() {
        UUID id = UUID.randomUUID();
        Account stored = new Account(UUID.randomUUID(), BigDecimal.TEN);
        when(accounts.findById(id)).thenReturn(Optional.of(stored));

        assertThat(service.getById(id)).isSameAs(stored);
    }

    @Test
    void unknownIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.getById(id));
    }
}
