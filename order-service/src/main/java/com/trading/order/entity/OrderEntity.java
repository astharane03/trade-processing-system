package com.trading.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "side", nullable = false)
    private String side;             // BUY or SELL

    @Column(name = "type", nullable = false)
    private String type;             // LIMIT or MARKET

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;   // decreases as partial fills happen

    @Column(name = "price")
    private Double price;

    @Column(name = "status", nullable = false)
    private String status;           // PENDING, PARTIALLY_FILLED, FILLED, CANCELLED

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}