package com.trading.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_store")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;        // ORDER_PLACED, TRADE_EXECUTED

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;      // orderId or tradeId

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;          // full event as JSON string

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}