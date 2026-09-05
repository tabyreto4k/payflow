package ru.payflow.order.order.service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.order.exception.NotFoundException;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.repository.OrderRepository;

/**
 * Чтения вынесены из {@link OrderService} отдельным бином: вызов собственного метода прошёл бы мимо
 * прокси, и {@code @Transactional(readOnly = true)} на нём просто не сработал бы.
 */
@Service
public class OrderQueryService {

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID customerId, UUID orderId) {
        return OrderResponse.from(ownOrder(customerId, orderId));
    }

    /**
     * Страница заказов вместе с позициями за постоянное число запросов: сначала id страницы, потом
     * один join fetch по ним. Наивная выборка догружала бы позиции по запросу на заказ.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> list(UUID customerId, Pageable pageable) {
        Page<UUID> ids = orders.findIdsByCustomerId(customerId, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        Map<UUID, Order> byId = orders.findAllWithItems(ids.getContent()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return ids.map(id -> OrderResponse.from(byId.get(id)));
    }

    /** Чужой заказ отвечает как несуществующий: 403 подтвердил бы, что такой заказ есть. */
    Order ownOrder(UUID customerId, UUID orderId) {
        return orders.findWithItems(orderId)
                .filter(order -> order.getCustomerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Заказ %s не найден".formatted(orderId)));
    }
}
