package ru.payflow.order.order.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
        @NotNull UUID productId,
        @Positive int quantity,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price) {}
