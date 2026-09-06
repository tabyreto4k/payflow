package ru.payflow.order;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.payflow.order.auth.model.Role;
import ru.payflow.order.auth.model.User;
import ru.payflow.order.auth.repository.UserRepository;

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

    @Autowired
    private UserRepository users;

    /**
     * Покупатель с настоящей строкой в {@code users}: заказ уносит в событие его email, и случайный
     * UUID без пользователя до outbox теперь не доходит.
     */
    protected UUID registerCustomer() {
        User user = new User("it-" + UUID.randomUUID() + "@payflow.ru", "hash-не-проверяется", Role.USER);
        return users.save(user).getId();
    }
}
