package com.trading.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent {

    private String orderId;
    private String symbol;
    private String side;         // "BUY" or "SELL"
    private String type;         // "LIMIT" or "MARKET"
    private Integer quantity;
    private Double price;
    private String userId;
    private long timestamp;
}