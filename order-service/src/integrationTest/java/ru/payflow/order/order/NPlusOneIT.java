package ru.payflow.order.order;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;
import ru.payflow.order.PostgresIT;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderItemRequest;
import ru.payflow.order.order.model.Order;
import ru.payflow.order.order.service.OrderQueryService;
import ru.payflow.order.order.service.OrderService;

/**
 * Витрина заказов вместе с позициями. Наивная выборка догружает позиции по запросу на заказ, поэтому
 * счётчик SQL растёт вместе с числом заказов; постраничная выборка по id плюс один join fetch держит
 * его постоянным.
 */
@SpringBootTest
class NPlusOneIT extends PostgresIT {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private OrderService orders;

    @Autowired
    private OrderQueryService queries;

    private Statistics statistics;

    @BeforeEach
    void enableStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    void naiveListingQueriesItemsPerOrder() {
        UUID customerId = customerWithOrders(5);

        long queryCount = countQueries(() -> {
            List<Order> page = entityManager
                    .createQuery("select o from Order o where o.customerId = :customerId", Order.class)
                    .setParameter("customerId", customerId)
                    .setMaxResults(10)
                    .getResultList();
            page.forEach(order -> order.getItems().size());
        });

        assertThat(queryCount).isGreaterThan(2);
    }

    @Test
    void fetchJoinKeepsQueryCountConstant() {
        UUID five = customerWithOrders(5);
        UUID ten = customerWithOrders(10);

        long forFive = countQueries(() -> queries.list(five, PageRequest.of(0, 20)));
        long forTen = countQueries(() -> queries.list(ten, PageRequest.of(0, 20)));

        assertThat(forFive).isEqualTo(forTen).isLessThanOrEqualTo(3);
    }

    private long countQueries(Runnable work) {
        return transaction.execute(status -> {
            entityManager.clear();
            statistics.clear();
            work.run();
            return statistics.getPrepareStatementCount();
        });
    }

    private UUID customerWithOrders(int count) {
        UUID customerId = registerCustomer();
        for (int i = 0; i < count; i++) {
            orders.create(
                    customerId,
                    new CreateOrderRequest(List.of(
                            new OrderItemRequest(UUID.randomUUID(), 1, new BigDecimal("1.00")),
                            new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("2.00")))));
        }
        return customerId;
    }
}
