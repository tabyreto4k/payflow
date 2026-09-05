package ru.payflow.payment.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.repository.AccountRepository;
import ru.payflow.payment.consumer.repository.ProcessedEventRepository;
import ru.payflow.payment.outbox.model.OutboxEvent;
import ru.payflow.payment.outbox.repository.OutboxRepository;
import ru.payflow.payment.payment.model.Payment;
import ru.payflow.payment.payment.model.PaymentStatus;
import ru.payflow.payment.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock
    private AccountRepository accounts;

    @Mock
    private PaymentRepository payments;

    @Mock
    private ProcessedEventRepository processedEvents;

    @Mock
    private OutboxRepository outbox;

    // Настоящий маппер: тест читает payload события, подделанная сериализация проверяла бы себя.
    @Spy
    private ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private PaymentService service;

    @Test
    void enoughMoneyChargesTheAccountAndReportsCompletion() throws Exception {
        OrderCreatedEvent event = orderFor("40.00");
        Account account = accountWith("100.00");
        when(accounts.findByCustomerIdForUpdate(CUSTOMER)).thenReturn(Optional.of(account));

        service.processOrder(event);

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
        assertThat(savedPayment().getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        OutboxEvent published = publishedEvent();
        assertThat(published.getTopic()).isEqualTo(Topics.PAYMENTS_COMPLETED);
        assertThat(published.getKey()).isEqualTo(event.orderId().toString());
        assertThat(json.readValue(published.getPayload(), PaymentCompletedEvent.class)
                        .orderId())
                .isEqualTo(event.orderId());
    }

    @Test
    void shortMoneyLeavesTheBalanceAndReportsFailure() throws Exception {
        OrderCreatedEvent event = orderFor("140.00");
        Account account = accountWith("100.00");
        when(accounts.findByCustomerIdForUpdate(CUSTOMER)).thenReturn(Optional.of(account));

        service.processOrder(event);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        assertThat(savedPayment().getStatus()).isEqualTo(PaymentStatus.FAILED);

        OutboxEvent published = publishedEvent();
        assertThat(published.getTopic()).isEqualTo(Topics.PAYMENTS_FAILED);
        assertThat(json.readValue(published.getPayload(), PaymentFailedEvent.class)
                        .reason())
                .isEqualTo("insufficient_funds");
    }

    @Test
    void missingAccountIsAFailedOutcomeAndNotAStuckOrder() throws Exception {
        OrderCreatedEvent event = orderFor("10.00");
        when(accounts.findByCustomerIdForUpdate(CUSTOMER)).thenReturn(Optional.empty());

        service.processOrder(event);

        // Иначе заказ навсегда завис бы в AWAITING_PAYMENT: исход саги ему принести некому.
        assertThat(savedPayment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(json.readValue(publishedEvent().getPayload(), PaymentFailedEvent.class)
                        .reason())
                .isEqualTo("account_not_found");
    }

    @Test
    void alreadyProcessedEventChangesNothing() {
        OrderCreatedEvent event = orderFor("40.00");
        when(processedEvents.existsById(event.eventId())).thenReturn(true);

        service.processOrder(event);

        verify(accounts, never()).findByCustomerIdForUpdate(any());
        verify(payments, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void secondEventAboutAPaidOrderChargesNothing() {
        OrderCreatedEvent event = orderFor("40.00");
        when(payments.findByOrderId(event.orderId()))
                .thenReturn(Optional.of(Payment.completed(event.orderId(), CUSTOMER, new BigDecimal("40.00"))));

        service.processOrder(event);

        // eventId другой, поэтому первый слой пропустил. Ловит второй — уникальность по заказу.
        verify(accounts, never()).findByCustomerIdForUpdate(any());
        verify(outbox, never()).save(any());
    }

    private static OrderCreatedEvent orderFor(String amount) {
        return new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), CUSTOMER, new BigDecimal(amount), Instant.now());
    }

    private static Account accountWith(String balance) {
        return new Account(CUSTOMER, new BigDecimal(balance));
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        return captor.getValue();
    }

    private OutboxEvent publishedEvent() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        return captor.getValue();
    }
}
