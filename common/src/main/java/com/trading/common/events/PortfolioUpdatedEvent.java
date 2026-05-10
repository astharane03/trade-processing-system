package com.trading.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpdatedEvent {

    private String userId;
    private String symbol;
    private Integer newQuantity;      // total shares held after trade
    private Double averageCost;       // average cost per share
    private Double realizedPnL;       // profit/loss from closed positions
    private Double unrealizedPnL;     // profit/loss on open positions
    private long timestamp;
}