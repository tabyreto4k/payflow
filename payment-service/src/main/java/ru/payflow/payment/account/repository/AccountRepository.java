package ru.payflow.payment.account.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.payflow.payment.account.model.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByCustomerId(UUID customerId);

    /** Берёт строку под {@code SELECT ... FOR UPDATE}: параллельные списания выстраиваются в очередь. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.customerId = :customerId")
    Optional<Account> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
