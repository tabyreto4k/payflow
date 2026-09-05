package ru.payflow.order.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.payflow.events.OrderCreatedEvent;
import ru.payflow.events.PaymentCompletedEvent;
import ru.payflow.events.PaymentFailedEvent;
import ru.payflow.events.Topics;
import ru.payflow.order.consumer.model.ProcessedEvent;
import ru.payflow.order.consumer.repository.ProcessedEventRepository;
import ru.payflow.order.exception.NotFoundException;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.model.OrderItem;
import ru.payflow.order.order.repository.OrderRepository;
import ru.payflow.order.outbox.model.OutboxEvent;
import ru.payflow.order.outbox.repository.OutboxRepository;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderQueryService queries;
    private final OutboxRepository outbox;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper json;

    public OrderService(
            OrderRepository orders,
            OrderQueryService queries,
            OutboxRepository outbox,
            ProcessedEventRepository processedEvents,
            ObjectMapper json) {
        this.orders = orders;
        this.queries = queries;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.json = json;
    }

    /**
     * Заказ и событие о нём пишутся одной транзакцией: отправлять в Kafka из бизнес-транзакции
     * нельзя — брокер не участвует в откате, и на упавшем коммите ушло бы событие о заказе,
     * которого нет. Дальше заказ двигает консьюмер исходов оплаты.
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
        Order saved = orders.saveAndFlush(order);
        outbox.save(orderCreated(saved));
        return OrderResponse.from(saved);
    }

    private OutboxEvent orderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), order.getId(), order.getCustomerId(), order.getTotal(), Instant.now());
        return new OutboxEvent(Topics.ORDERS_CREATED, order.getId().toString(), serialize(event));
    }

    private String serialize(OrderCreatedEvent event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Контракт — records из events-contract, они сериализуются всегда. Сюда попадём только
            // если контракт сломали, и это баг сборки, а не ответ клиенту.
            throw new IllegalStateException("Событие не сериализуется: " + event, e);
        }
    }

    /** Исход саги: деньги списаны. */
    @Transactional
    public void applyPaid(PaymentCompletedEvent event) {
        apply(event.eventId(), event.orderId(), Order::markPaid);
    }

    /** Исход саги: оплата не прошла, заказ компенсируется отменой. */
    @Transactional
    public void applyFailed(PaymentFailedEvent event) {
        apply(event.eventId(), event.orderId(), Order::cancel);
    }

    /**
     * Отметка о событии и переход статуса — одной транзакцией. Повтор доставки отсекается по
     * {@code eventId}: без этого второй {@code markPaid} упал бы на недопустимом переходе, и
     * исправное по сути сообщение уехало бы в DLT.
     */
    private void apply(UUID eventId, UUID orderId, Consumer<Order> transition) {
        if (processedEvents.existsById(eventId)) {
            return;
        }
        processedEvents.save(new ProcessedEvent(eventId));
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Заказ %s не найден".formatted(orderId)));
        transition.accept(order);
    }

    @Transactional
    public OrderResponse cancel(UUID customerId, UUID orderId) {
        Order order = queries.ownOrder(customerId, orderId);
        order.cancel();
        return OrderResponse.from(order);
    }
}
