package ru.payflow.order;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Один Postgres на все интеграционные тесты модуля: контейнер поднимается при первой загрузке
 * класса и живёт до конца JVM — перезапускать его на каждый тестовый класс дорого.
 *
 * <p>Профиль {@code it} лежит отдельным файлом, а не {@code application.yml}: одноимённый файл
 * в тестовом source set затенил бы основной конфиг целиком, а не дополнил его.
 */
@ActiveProfiles("it")
public abstract class PostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
