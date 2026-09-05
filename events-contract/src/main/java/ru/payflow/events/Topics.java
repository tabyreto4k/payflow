package ru.payflow.events;

/** Имена топиков саги. Держатся здесь, чтобы продюсер и консьюмер не разошлись в строке. */
public final class Topics {

    public static final String ORDERS_CREATED = "orders.created";
    public static final String PAYMENTS_COMPLETED = "payments.completed";
    public static final String PAYMENTS_FAILED = "payments.failed";

    private Topics() {}
}
