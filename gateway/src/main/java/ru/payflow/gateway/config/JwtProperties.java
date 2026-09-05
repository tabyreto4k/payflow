package ru.payflow.gateway.config;

import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Секрет тот же, что у order-service: там токены подписывают, здесь только проверяют. */
@Validated
@ConfigurationProperties("payflow.jwt")
public record JwtProperties(@Size(min = 32) String secret) {}
