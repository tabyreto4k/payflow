package ru.payflow.payment.consumer.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.payflow.payment.consumer.model.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
