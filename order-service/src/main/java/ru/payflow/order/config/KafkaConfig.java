package ru.payflow.order.config;

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

    private static final String DLT_SUFFIX = ".DLT";
    private static final int PARTITIONS = 3;

    /**
     * Топик объявляется приложением: в compose авто-создание выключено, иначе опечатка в имени
     * тихо породила бы новый топик вместо ошибки.
     *
     * <p>Партиций больше одной, порядок держится ключом: события одного заказа уходят с ключом
     * {@code orderId} и потому всегда попадают в одну партицию.
     */
    @Bean
    public NewTopic ordersCreated() {
        return topic(Topics.ORDERS_CREATED);
    }

    /**
     * DLT для потребляемых топиков объявляет тот, кто в них пишет, — то есть этот сервис. Число
     * партиций совпадает с исходным: запись уходит в партицию с тем же номером.
     */
    @Bean
    public NewTopic paymentsCompletedDlt() {
        return topic(Topics.PAYMENTS_COMPLETED + DLT_SUFFIX);
    }

    @Bean
    public NewTopic paymentsFailedDlt() {
        return topic(Topics.PAYMENTS_FAILED + DLT_SUFFIX);
    }

    /**
     * Три попытки, дальше — в DLT. Бесконечный ретрай отравленной записи остановил бы всю
     * партицию, а с ней и все остальные заказы.
     *
     * <p>Имя назначения задано явно: по умолчанию Spring Kafka лепит суффикс {@code -dlt}, а в ТЗ
     * и в мониторинге принято {@code <топик>.DLT}.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }
}
