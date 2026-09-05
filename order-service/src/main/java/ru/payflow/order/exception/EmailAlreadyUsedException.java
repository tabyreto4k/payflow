package ru.payflow.order.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Пользователь с адресом %s уже зарегистрирован".formatted(email));
    }
}
