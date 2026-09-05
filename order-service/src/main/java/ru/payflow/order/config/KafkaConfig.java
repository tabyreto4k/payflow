package ru.payflow.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.payflow.events.Topics;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class KafkaConfig {

    /**
     * Топик объявляется приложением: в compose авто-создание выключено, иначе опечатка в имени
     * тихо породила бы новый топик вместо ошибки.
     *
     * <p>Партиций больше одной, порядок держится ключом: события одного заказа уходят с ключом
     * {@code orderId} и потому всегда попадают в одну партицию.
     */
    @Bean
    public NewTopic ordersCreated() {
        return TopicBuilder.name(Topics.ORDERS_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
