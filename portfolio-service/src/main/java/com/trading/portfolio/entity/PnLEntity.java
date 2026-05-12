package com.trading.portfolio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//append only

@Entity
@Table(name = "portfolio_pnl")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PnLEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "trade_id", nullable = false)
    private String tradeId;

    @Column(name = "realized_pnl", nullable = false)
    private Double realizedPnl;        // profit/loss locked in from this trade

    @Column(name = "quantity_traded", nullable = false)
    private Integer quantityTraded;

    @Column(name = "execution_price", nullable = false)
    private Double executionPrice;

    @Column(name = "side", nullable = false)
    private String side;               // BUY or SELL

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;
}