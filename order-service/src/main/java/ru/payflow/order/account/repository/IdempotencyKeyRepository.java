package ru.payflow.order.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.payflow.order.account.model.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {}
