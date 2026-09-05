package ru.payflow.gateway;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;

/**
 * Rate limiter Spring Cloud Gateway живёт в Redis, поэтому контейнер нужен даже тестам
 * маршрутизации. Один на всю JVM: подъём на каждый класс дороже самих тестов.
 */
public abstract class RedisIT {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }
}
