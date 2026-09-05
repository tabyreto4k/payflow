package ru.payflow.order.order.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.order.account.service.AccountService;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.model.OrderItem;
import ru.payflow.order.order.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderQueryService queries;
    private final AccountService accounts;

    public OrderService(OrderRepository orders, OrderQueryService queries, AccountService accounts) {
        this.orders = orders;
        this.queries = queries;
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
        // saveAndFlush, а не save: @CreationTimestamp проставляется на вставке, и без сброса
        // в ответе на создание заказа уехал бы createdAt: null.
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse cancel(UUID customerId, UUID orderId) {
        Order order = queries.ownOrder(customerId, orderId);
        order.cancel();
        return OrderResponse.from(order);
    }
}
