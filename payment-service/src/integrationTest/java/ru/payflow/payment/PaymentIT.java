package ru.payflow.payment;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Postgres и Redis на все интеграционные тесты модуля: контейнеры поднимаются при первой загрузке
 * класса и живут до конца JVM — перезапускать их на каждый тестовый класс дорого.
 *
 * <p>Профиль {@code it} лежит отдельным файлом, а не {@code application.yml}: одноимённый файл
 * в тестовом source set затенил бы основной конфиг целиком, а не дополнил его.
 */
@ActiveProfiles("it")
public abstract class PaymentIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Кэш баланса живёт здесь, без Redis контекст сервиса не поднимется. */
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }
}
