package ru.payflow.order.order.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderResponse;
import ru.payflow.order.order.service.OrderQueryService;
import ru.payflow.order.order.service.OrderService;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orders;
    private final OrderQueryService queries;

    public OrderController(OrderService orders, OrderQueryService queries) {
        this.orders = orders;
        this.queries = queries;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("X-Customer-Id") UUID customerId, @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse order = orders.create(customerId, request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id()))
                .body(order);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@RequestHeader("X-Customer-Id") UUID customerId, @PathVariable UUID id) {
        return queries.getById(customerId, id);
    }

    @GetMapping
    public PagedModel<OrderResponse> list(@RequestHeader("X-Customer-Id") UUID customerId, Pageable pageable) {
        return new PagedModel<>(queries.list(customerId, pageable));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@RequestHeader("X-Customer-Id") UUID customerId, @PathVariable UUID id) {
        return orders.cancel(customerId, id);
    }
}
