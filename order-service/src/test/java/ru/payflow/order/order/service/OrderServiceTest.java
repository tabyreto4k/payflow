package ru.payflow.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.Topics;
import ru.payflow.order.exception.IllegalStateTransitionException;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderItemRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.model.OrderItem;
import ru.payflow.order.order.model.OrderStatus;
import ru.payflow.order.order.repository.OrderRepository;
import ru.payflow.order.outbox.model.OutboxEvent;
import ru.payflow.order.outbox.repository.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock
    private OrderRepository orders;

    @Mock
    private OrderQueryService queries;

    @Mock
    private OutboxRepository outbox;

    // Настоящий маппер, а не мок: тест проверяет содержимое payload, подделанная сериализация
    // проверяла бы саму себя.
    @Spy
    private ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private OrderService service;

    @Test
    void createdOrderWaitsForPayment() {
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        OrderResponse response = service.create(CUSTOMER, request("20.00", 2));

        // Дальше AWAITING_PAYMENT заказ сам не двигается: его сдвинет исход оплаты из Kafka.
        assertThat(response.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(response.total()).isEqualByComparingTo("40.00");
    }

    @Test
    void createdOrderPutsItsEventIntoOutbox() throws Exception {
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        OrderResponse response = service.create(CUSTOMER, request("20.00", 2));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        OutboxEvent stored = captor.getValue();

        assertThat(stored.getTopic()).isEqualTo(Topics.ORDERS_CREATED);
        // Ключ партиции — id заказа: события одного заказа не должны обгонять друг друга.
        assertThat(stored.getKey()).isEqualTo(response.id().toString());

        OrderCreatedEvent event = json.readValue(stored.getPayload(), OrderCreatedEvent.class);
        assertThat(event.orderId()).isEqualTo(response.id());
        assertThat(event.customerId()).isEqualTo(CUSTOMER);
        assertThat(event.amount()).isEqualByComparingTo("40.00");
        assertThat(event.eventId()).isNotNull();
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

    /** id заказу проставляет Hibernate на persist — в юните это делает тест. */
    private static Order persisted(Order order) {
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private static Order order() {
        return new Order(CUSTOMER, List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
    }

    private static CreateOrderRequest request(String price, int quantity) {
        return new CreateOrderRequest(
                List.of(new OrderItemRequest(UUID.randomUUID(), quantity, new BigDecimal(price))));
    }
}
