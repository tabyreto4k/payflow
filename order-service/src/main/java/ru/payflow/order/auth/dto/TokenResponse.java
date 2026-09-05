package ru.payflow.order.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
