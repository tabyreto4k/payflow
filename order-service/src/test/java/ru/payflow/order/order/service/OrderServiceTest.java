package ru.payflow.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.payflow.order.exception.IllegalStateTransitionException;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderItemRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.model.OrderItem;
import ru.payflow.order.order.model.OrderStatus;
import ru.payflow.order.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock
    private OrderRepository orders;

    @Mock
    private OrderQueryService queries;

    @InjectMocks
    private OrderService service;

    @Test
    void createdOrderWaitsForPayment() {
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.create(CUSTOMER, request("20.00", 2));

        // Оплата уехала в payment-service и ещё не подключена через Kafka: дальше AWAITING_PAYMENT
        // заказ сам не двигается.
        assertThat(response.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(response.total()).isEqualByComparingTo("40.00");
    }

    @Test
    void paidOrderCannotBeCancelled() {
        UUID orderId = UUID.randomUUID();
        Order paid = order();
        paid.awaitPayment();
        paid.markPaid();
        when(queries.ownOrder(CUSTOMER, orderId)).thenReturn(paid);

        assertThatExceptionOfType(IllegalStateTransitionException.class)
                .isThrownBy(() -> service.cancel(CUSTOMER, orderId));
    }

    @Test
    void awaitingOrderIsCancelled() {
        UUID orderId = UUID.randomUUID();
        Order awaiting = order();
        awaiting.awaitPayment();
        when(queries.ownOrder(CUSTOMER, orderId)).thenReturn(awaiting);

        assertThat(service.cancel(CUSTOMER, orderId).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    private static Order order() {
        return new Order(CUSTOMER, List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
    }

    private static CreateOrderRequest request(String price, int quantity) {
        return new CreateOrderRequest(
                List.of(new OrderItemRequest(UUID.randomUUID(), quantity, new BigDecimal(price))));
    }
}
