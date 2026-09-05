package ru.payflow.order.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Секрет общий с gateway: order-service подписывает, gateway проверяет. HS256 требует ключа не
 * короче 256 бит — отсюда нижняя граница длины.
 */
@Validated
@ConfigurationProperties("payflow.jwt")
public record JwtProperties(@Size(min = 32) String secret, @NotNull Duration accessTtl, @NotNull Duration refreshTtl) {}
