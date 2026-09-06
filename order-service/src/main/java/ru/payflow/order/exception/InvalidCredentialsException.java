package ru.payflow.order.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Неверный адрес или пароль");
    }
}
