package ru.payflow.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import ru.payflow.events.Topics;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class KafkaConfig {

    private static final String DLT_SUFFIX = ".DLT";
    private static final int PARTITIONS = 3;

    /**
     * DLT объявляются явно: авто-создание топиков в compose выключено, а recoverer кладёт запись
     * в партицию с тем же номером — при меньшем их числе отправка бы не состоялась.
     */
    @Bean
    public NewTopic paymentsCompletedDlt() {
        return TopicBuilder.name(Topics.PAYMENTS_COMPLETED + DLT_SUFFIX)
                .partitions(PARTITIONS)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentsFailedDlt() {
        return TopicBuilder.name(Topics.PAYMENTS_FAILED + DLT_SUFFIX)
                .partitions(PARTITIONS)
                .replicas(1)
                .build();
    }

    /**
     * Недоступный SMTP — беда временная, поэтому три попытки с растущей паузой (1с, 2с, 4с).
     * Бесконечно повторять нельзя: одно неотправляемое письмо остановило бы всю партицию, а с ней
     * и уведомления по остальным заказам. Что не ушло за три попытки — уезжает в DLT с ERROR-логом.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<String, String> template) {
        // Имя назначения задано явно: по умолчанию Spring Kafka лепит суффикс "-dlt", а в ТЗ
        // и в мониторинге принято "<топик>.DLT".
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
