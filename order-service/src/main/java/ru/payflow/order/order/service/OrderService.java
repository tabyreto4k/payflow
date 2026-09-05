package ru.payflow.order.order.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.model.OrderItem;
import ru.payflow.order.order.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderQueryService queries;

    public OrderService(OrderRepository orders, OrderQueryService queries) {
        this.orders = orders;
        this.queries = queries;
    }

    /**
     * Заказ уходит в {@code AWAITING_PAYMENT} и там остаётся: счета уехали в payment-service, а
     * Kafka ещё нет. Место списания займёт запись в outbox — до неё заказ не оплачивается.
     */
    @Transactional
    public OrderResponse create(UUID customerId, CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), item.price()))
                .toList();

        Order order = new Order(customerId, items);
        order.awaitPayment();
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
