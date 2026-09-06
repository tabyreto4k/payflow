package ru.payflow.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.notification.config.MailProperties;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String CUSTOMER = "ivan@payflow.ru";
    private static final String FROM = "no-reply@payflow.ru";
    private static final UUID ORDER = UUID.randomUUID();

    @Mock
    private JavaMailSender mail;

    private NotificationService service;

    private NotificationService service() {
        if (service == null) {
            service = new NotificationService(mail, new MailProperties(FROM));
        }
        return service;
    }

    @Test
    void paidOrderLetterGoesToTheCustomerFromTheEvent() {
        service()
                .notifyPaid(new PaymentCompletedEvent(
                        UUID.randomUUID(), ORDER, CUSTOMER, new BigDecimal("40.00"), Instant.now()));

        SimpleMailMessage sent = captured();
        assertThat(sent.getTo()).containsExactly(CUSTOMER);
        assertThat(sent.getFrom()).isEqualTo(FROM);
        assertThat(sent.getSubject()).isEqualTo("Заказ оплачен");
        assertThat(sent.getText()).contains(ORDER.toString()).contains("40.00");
    }

    @Test
    void failedPaymentLetterExplainsTheReasonInWords() {
        service()
                .notifyFailed(new PaymentFailedEvent(
                        UUID.randomUUID(),
                        ORDER,
                        CUSTOMER,
                        new BigDecimal("1000.00"),
                        "insufficient_funds",
                        Instant.now()));

        SimpleMailMessage sent = captured();
        assertThat(sent.getTo()).containsExactly(CUSTOMER);
        assertThat(sent.getSubject()).isEqualTo("Оплата не прошла");
        // Машинную причину читает мониторинг, а не покупатель.
        assertThat(sent.getText()).contains("недостаточно средств").doesNotContain("insufficient_funds");
    }

    @Test
    void unknownReasonIsPassedThroughInsteadOfBeingSwallowed() {
        service()
                .notifyFailed(new PaymentFailedEvent(
                        UUID.randomUUID(), ORDER, CUSTOMER, new BigDecimal("1.00"), "gateway_timeout", Instant.now()));

        // Молчаливое «что-то пошло не так» скрыло бы новую причину и от покупателя, и от нас.
        assertThat(captured().getText()).contains("gateway_timeout");
    }

    private SimpleMailMessage captured() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        return captor.getValue();
    }
}
