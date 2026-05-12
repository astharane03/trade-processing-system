package com.trading.portfolio.repository;

import com.trading.portfolio.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEventEntity, String> {
    boolean existsById(String eventId);
}