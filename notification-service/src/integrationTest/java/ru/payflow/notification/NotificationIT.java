package ru.payflow.notification;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Три контейнера на все интеграционные тесты модуля: брокер, Redis под дедуп и MailHog в роли
 * SMTP. Поднимаются при первой загрузке класса и живут до конца JVM — перезапуск на каждый
 * тестовый класс дороже самих тестов.
 */
public abstract class NotificationIT {

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    protected static final GenericContainer<?> MAILHOG =
            new GenericContainer<>("mailhog/mailhog:v1.0.1").withExposedPorts(1025, 8025);

    static {
        KAFKA.start();
        REDIS.start();
        MAILHOG.start();
    }

    protected static String mailhogApi() {
        return "http://" + MAILHOG.getHost() + ":" + MAILHOG.getMappedPort(8025);
    }
}
