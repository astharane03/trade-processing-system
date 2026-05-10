package com.trading.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutedEvent {

    private String tradeId;
    private String buyOrderId;
    private String sellOrderId;
    private String symbol;
    private Integer quantity;        // how many shares actually traded
    private Double executionPrice;   // price at which trade happened
    private String buyUserId;
    private String sellUserId;
    private long timestamp;
}