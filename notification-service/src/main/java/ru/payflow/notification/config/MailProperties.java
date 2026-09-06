package ru.payflow.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Отправитель писем. Адрес получателя приезжает в событии, а обратный адрес — свойство стенда.
 *
 * @param from значение поля From у всех писем сервиса
 */
@ConfigurationProperties(prefix = "payflow.mail")
public record MailProperties(String from) {}
