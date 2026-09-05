package ru.payflow.payment.outbox.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.payflow.payment.outbox.model.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Пачка неотправленных событий, забранная под блокировку. {@code SKIP LOCKED} — чтобы второй
     * инстанс поллера не ждал первого, а сразу взял следующие: иначе горизонтальное масштабирование
     * превращается в очередь из одного работника.
     */
    @Query(
            value =
                    """
                    SELECT * FROM outbox
                    WHERE sent_at IS NULL
                    ORDER BY id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<OutboxEvent> lockUnsentBatch(@Param("limit") int limit);
}
