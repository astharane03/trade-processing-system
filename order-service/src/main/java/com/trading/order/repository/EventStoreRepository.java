package com.trading.order.repository;

import com.trading.order.entity.EventStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventStoreRepository extends JpaRepository<EventStoreEntity, Long> {

    List<EventStoreEntity> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);

    List<EventStoreEntity> findByEventTypeOrderByCreatedAtAsc(String eventType);
}