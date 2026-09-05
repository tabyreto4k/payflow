package ru.payflow.order.order.repository;

import java.util.Collection;
import java.util.List;
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

    /**
     * Страница берётся отдельным запросом по id, а не join fetch с limit: Hibernate на коллекцию
     * с limit молча уходит в пагинацию в памяти (HHH90003004) — на большой витрине это OOM.
     */
    @Query("select o.id from Order o where o.customerId = :customerId")
    Page<UUID> findIdsByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    @Query("select o from Order o join fetch o.items where o.id in :ids")
    List<Order> findAllWithItems(@Param("ids") Collection<UUID> ids);
}
