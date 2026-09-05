package ru.payflow.order.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import ru.payflow.order.exception.IllegalStateTransitionException;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Order() {}

    public Order(UUID customerId, List<OrderItem> items) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId обязателен");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Заказ без позиций не имеет смысла");
        }
        this.customerId = customerId;
        this.status = OrderStatus.NEW;
        for (OrderItem item : items) {
            item.assignTo(this);
            this.items.add(item);
        }
        this.total = this.items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void awaitPayment() {
        transitionTo(OrderStatus.AWAITING_PAYMENT, Set.of(OrderStatus.NEW));
    }

    public void markPaid() {
        transitionTo(OrderStatus.PAID, Set.of(OrderStatus.AWAITING_PAYMENT));
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED, Set.of(OrderStatus.AWAITING_PAYMENT));
    }

    private void transitionTo(OrderStatus target, Set<OrderStatus> allowedFrom) {
        if (!allowedFrom.contains(status)) {
            throw new IllegalStateTransitionException(status, target);
        }
        status = target;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
