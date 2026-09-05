package ru.payflow.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.payflow.order.exception.NotFoundException;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.model.OrderItem;
import ru.payflow.order.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock
    private OrderRepository orders;

    @InjectMocks
    private OrderQueryService service;

    @Test
    void readsOwnOrder() {
        UUID orderId = UUID.randomUUID();
        when(orders.findWithItems(orderId)).thenReturn(Optional.of(order()));

        assertThat(service.getById(CUSTOMER, orderId).customerId()).isEqualTo(CUSTOMER);
    }

    @Test
    void strangersOrderLooksMissing() {
        UUID orderId = UUID.randomUUID();
        when(orders.findWithItems(orderId)).thenReturn(Optional.of(order()));

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getById(UUID.randomUUID(), orderId));
    }

    @Test
    void unknownOrderIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orders.findWithItems(orderId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.getById(CUSTOMER, orderId));
    }

    private static Order order() {
        return new Order(CUSTOMER, List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))));
    }
}
