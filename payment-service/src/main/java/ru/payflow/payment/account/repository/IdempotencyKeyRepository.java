package ru.payflow.payment.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.payflow.payment.account.model.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {}
