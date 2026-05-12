package com.trading.portfolio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_holdings",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "symbol"}
        ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;          // total shares held right now

    @Column(name = "avg_cost", nullable = false)
    private Double avgCost;            // average price paid per share

    @Column(name = "total_invested", nullable = false)
    private Double totalInvested;      // quantity * avgCost

    @Column(name = "unrealized_pnl")
    private Double unrealizedPnl;      // based on last known market price

    @Column(name = "last_updated")
    private Long lastUpdated;
}