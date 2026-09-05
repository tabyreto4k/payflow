package ru.payflow.order.consumer.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.payflow.order.consumer.model.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
