package ru.payflow.order.order.dto;

import java.math.BigDecimal;
import java.util.UUID;
import ru.payflow.order.order.model.OrderItem;

public record OrderItemResponse(UUID productId, int quantity, BigDecimal price) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getQuantity(), item.getPrice());
    }
}
