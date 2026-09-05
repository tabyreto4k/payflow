package ru.payflow.order.order.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.payflow.order.order.model.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("select o from Order o join fetch o.items where o.id = :id")
    Optional<Order> findWithItems(@Param("id") UUID id);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}
