package com.trading.order.orderbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private String orderId;
    private String userId;
    private String symbol;
    private String side;             // BUY or SELL
    private String type;             // LIMIT or MARKET
    private Integer quantity;
    private Integer remainingQuantity;
    private Double price;
    private Long timestamp;          // price-time priority tiebreaker
}