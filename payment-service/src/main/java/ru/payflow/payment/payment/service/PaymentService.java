package ru.payflow.payment.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
import ru.payflow.payment.account.model.Account;
import ru.payflow.payment.account.repository.AccountRepository;
import ru.payflow.payment.consumer.model.ProcessedEvent;
import ru.payflow.payment.consumer.repository.ProcessedEventRepository;
import ru.payflow.payment.logging.CorrelationId;
import ru.payflow.payment.outbox.model.OutboxEvent;
import ru.payflow.payment.outbox.repository.OutboxRepository;
import ru.payflow.payment.payment.model.Payment;
import ru.payflow.payment.payment.repository.PaymentRepository;

@Service
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);

    static final String INSUFFICIENT_FUNDS = "insufficient_funds";
    static final String ACCOUNT_NOT_FOUND = "account_not_found";

    private final AccountRepository accounts;
    private final PaymentRepository payments;
    private final ProcessedEventRepository processedEvents;
    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public PaymentService(
            AccountRepository accounts,
            PaymentRepository payments,
            ProcessedEventRepository processedEvents,
            OutboxRepository outbox,
            ObjectMapper json) {
        this.accounts = accounts;
        this.payments = payments;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.json = json;
    }

    /**
     * Списывает деньги за заказ и кладёт исход в outbox — всё одной транзакцией, чтобы деньги и
     * событие о них не разошлись.
     *
     * <p>Идемпотентность двухслойная, потому что дубли бывают двух разных сортов. Повторная
     * доставка того же события отсекается по {@code eventId}. Другое событие про уже оплаченный
     * заказ — по {@code payments.order_id}: первый слой его пропустил бы, eventId-то новый.
     * Проверки в коде страхуют первичный ключ и уникальный индекс: между проверкой и вставкой
     * успевает вклиниться параллельный обработчик, и тогда транзакция откатится, а сообщение
     * приедет снова — уже к отработавшей проверке.
     */
    @Transactional
    public void processOrder(OrderCreatedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            LOG.info("Событие {} уже обработано, пропуск", event.eventId());
            return;
        }
        processedEvents.save(new ProcessedEvent(event.eventId()));

        if (payments.findByOrderId(event.orderId()).isPresent()) {
            LOG.info("Заказ {} уже оплачен, повторное событие {} пропущено", event.orderId(), event.eventId());
            return;
        }

        accounts.findByCustomerIdForUpdate(event.customerId())
                .ifPresentOrElse(account -> charge(account, event), () -> fail(event, ACCOUNT_NOT_FOUND));
    }

    private void charge(Account account, OrderCreatedEvent event) {
        if (account.getBalance().compareTo(event.amount()) < 0) {
            fail(event, INSUFFICIENT_FUNDS);
            return;
        }
        account.withdraw(event.amount());
        payments.save(Payment.completed(event.orderId(), event.customerId(), event.amount()));
        publish(
                Topics.PAYMENTS_COMPLETED,
                event.orderId(),
                new PaymentCompletedEvent(
                        UUID.randomUUID(), event.orderId(), event.customerEmail(), event.amount(), Instant.now()));
    }

    /** Нехватка денег — законный исход саги, а не сбой: заказу нужно узнать о нём и отмениться. */
    private void fail(OrderCreatedEvent event, String reason) {
        payments.save(Payment.failed(event.orderId(), event.customerId(), event.amount()));
        publish(
                Topics.PAYMENTS_FAILED,
                event.orderId(),
                new PaymentFailedEvent(
                        UUID.randomUUID(),
                        event.orderId(),
                        event.customerEmail(),
                        event.amount(),
                        reason,
                        Instant.now()));
    }

    private void publish(String topic, UUID orderId, Object event) {
        // Идентификатор тот же, что у события, которое сюда привело: цепочка не рвётся на сервисе.
        outbox.save(new OutboxEvent(topic, orderId.toString(), serialize(event), CorrelationId.current()));
    }

    private String serialize(Object event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Контракт — records из events-contract, они сериализуются всегда. Сюда попадём только
            // если контракт сломали, и это баг сборки.
            throw new IllegalStateException("Событие не сериализуется: " + event, e);
        }
    }
}
