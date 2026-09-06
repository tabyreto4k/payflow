package ru.payflow.payment.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal initialBalance) {}
