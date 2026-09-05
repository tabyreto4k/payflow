package ru.payflow.order.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.payflow.order.exception.IllegalStateTransitionException;

class OrderTest {

    private static final UUID CUSTOMER = UUID.randomUUID();

    @Test
    void newOrderSumsItemsAndStartsAsNew() {
        Order order = order(item("10.00", 2), item("5.50", 1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getTotal()).isEqualByComparingTo("25.50");
        assertThat(order.getItems()).hasSize(2).allSatisfy(item -> assertThat(item.getOrder())
                .isSameAs(order));
    }

    @Test
    void orderWithoutItemsIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(CUSTOMER, List.of()));
    }

    @Test
    void orderWithoutCustomerIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(null, List.of(item("1.00", 1))));
    }

    @Test
    void itemRejectsNonPositiveQuantityAndPrice() {
        assertThatIllegalArgumentException().isThrownBy(() -> item("10.00", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> item("0.00", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> item("-1.00", 1));
    }

    @Test
    void paidOrderWalksTheHappyPath() {
        Order order = order(item("40.00", 1));

        order.awaitPayment();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void unpaidOrderIsCancelled() {
        Order order = order(item("40.00", 1));
        order.awaitPayment();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("forbiddenTransitions")
    void forbiddenTransitionIsRejected(OrderStatus from, OrderStatus to, Consumer<Order> transition) {
        Order order = orderIn(from);

        assertThatExceptionOfType(IllegalStateTransitionException.class).isThrownBy(() -> transition.accept(order));
        assertThat(order.getStatus()).isEqualTo(from);
    }

    static List<Arguments> forbiddenTransitions() {
        Consumer<Order> awaitPayment = Order::awaitPayment;
        Consumer<Order> markPaid = Order::markPaid;
        Consumer<Order> cancel = Order::cancel;
        return List.of(
                Arguments.of(OrderStatus.NEW, OrderStatus.PAID, markPaid),
                Arguments.of(OrderStatus.NEW, OrderStatus.CANCELLED, cancel),
                Arguments.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.AWAITING_PAYMENT, awaitPayment),
                Arguments.of(OrderStatus.PAID, OrderStatus.PAID, markPaid),
                Arguments.of(OrderStatus.PAID, OrderStatus.CANCELLED, cancel),
                Arguments.of(OrderStatus.PAID, OrderStatus.AWAITING_PAYMENT, awaitPayment),
                Arguments.of(OrderStatus.CANCELLED, OrderStatus.PAID, markPaid),
                Arguments.of(OrderStatus.CANCELLED, OrderStatus.CANCELLED, cancel));
    }

    private static Order orderIn(OrderStatus status) {
        Order order = order(item("40.00", 1));
        switch (status) {
            case NEW -> {}
            case AWAITING_PAYMENT -> order.awaitPayment();
            case PAID -> {
                order.awaitPayment();
                order.markPaid();
            }
            case CANCELLED -> {
                order.awaitPayment();
                order.cancel();
            }
        }
        return order;
    }

    private static Order order(OrderItem... items) {
        return new Order(CUSTOMER, List.of(items));
    }

    private static OrderItem item(String price, int quantity) {
        return new OrderItem(UUID.randomUUID(), quantity, new BigDecimal(price));
    }
}
