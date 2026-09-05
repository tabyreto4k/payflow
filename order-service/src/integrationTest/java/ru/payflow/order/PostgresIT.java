package ru.payflow.order;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Один Postgres на все интеграционные тесты модуля: контейнер поднимается при первой загрузке
 * класса и живёт до конца JVM — перезапускать его на каждый тестовый класс дорого.
 */
public abstract class PostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
