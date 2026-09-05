package ru.payflow.order.order.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.order.account.service.AccountService;
import ru.payflow.order.exception.NotFoundException;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.model.OrderItem;
import ru.payflow.order.order.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final AccountService accounts;

    public OrderService(OrderRepository orders, AccountService accounts) {
        this.orders = orders;
        this.accounts = accounts;
    }

    /**
     * Заказ и списание — в одной транзакции: пока нет Kafka, оплата синхронная. В Заходе 3 место
     * вызова {@code accounts.charge} займёт запись в outbox.
     */
    @Transactional
    public OrderResponse create(UUID customerId, CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), item.price()))
                .toList();

        Order order = new Order(customerId, items);
        order.awaitPayment();
        if (accounts.charge(customerId, order.getTotal())) {
            order.markPaid();
        } else {
            order.cancel();
        }
        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID customerId, UUID orderId) {
        return OrderResponse.from(ownOrder(customerId, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(UUID customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse cancel(UUID customerId, UUID orderId) {
        Order order = ownOrder(customerId, orderId);
        order.cancel();
        return OrderResponse.from(order);
    }

    /** Чужой заказ отвечает как несуществующий: 403 подтвердил бы, что такой заказ есть. */
    private Order ownOrder(UUID customerId, UUID orderId) {
        return orders.findWithItems(orderId)
                .filter(order -> order.getCustomerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Заказ %s не найден".formatted(orderId)));
    }
}
