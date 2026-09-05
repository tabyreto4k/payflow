package ru.payflow.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import ru.payflow.events.Topics;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class KafkaConfig {

    /** Исходы оплаты. Партиций столько же, сколько у входного топика, ключ — тот же orderId. */
    @Bean
    public NewTopic paymentsCompleted() {
        return TopicBuilder.name(Topics.PAYMENTS_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentsFailed() {
        return TopicBuilder.name(Topics.PAYMENTS_FAILED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    private static final String DLT_SUFFIX = ".DLT";

    /**
     * DLT объявляется явно: получатель пишет в неё сам, а авто-создание топиков в compose
     * выключено. Партиций столько же, сколько у исходного топика — сообщение уходит в партицию
     * с тем же номером, и при меньшем числе партиций отправка просто не состоялась бы.
     */
    @Bean
    public NewTopic ordersCreatedDlt() {
        return TopicBuilder.name(Topics.ORDERS_CREATED + DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Три попытки с паузой в секунду, дальше — в DLT. Повторять бесконечно нельзя: неразбираемое
     * сообщение остановило бы всю партицию, а вместе с ней и оплату всех остальных заказов.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<String, String> template) {
        // Имя назначения задано явно: по умолчанию Spring Kafka лепит суффикс "-dlt", а в ТЗ
        // и в мониторинге принято "<топик>.DLT".
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
