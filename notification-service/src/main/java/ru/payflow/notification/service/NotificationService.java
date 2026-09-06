package ru.payflow.notification.service;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.notification.config.MailProperties;

/** Письма об исходе оплаты. Адресат и сумма берутся из события: спросить их сервису негде. */
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private static final String INSUFFICIENT_FUNDS = "insufficient_funds";
    private static final String ACCOUNT_NOT_FOUND = "account_not_found";

    private final JavaMailSender mail;
    private final MailProperties properties;

    public NotificationService(JavaMailSender mail, MailProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    public void notifyPaid(PaymentCompletedEvent event) {
        send(
                event.customerEmail(),
                "Заказ оплачен",
                """
                Заказ %s оплачен.

                Списано: %s ₽.
                """
                        .formatted(event.orderId(), amount(event.amount())));
    }

    public void notifyFailed(PaymentFailedEvent event) {
        send(
                event.customerEmail(),
                "Оплата не прошла",
                """
                Оплатить заказ %s на сумму %s ₽ не удалось.

                Причина: %s.
                """
                        .formatted(event.orderId(), amount(event.amount()), explain(event.reason())));
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mail.send(message);
        LOG.info("Письмо «{}» отправлено на {}", subject, to);
    }

    /** Машинную причину из события человек в письме читать не должен. */
    private static String explain(String reason) {
        return switch (reason) {
            case INSUFFICIENT_FUNDS -> "недостаточно средств на счёте";
            case ACCOUNT_NOT_FOUND -> "счёт не найден";
            default -> reason;
        };
    }

    private static String amount(BigDecimal value) {
        return value.toPlainString();
    }
}
