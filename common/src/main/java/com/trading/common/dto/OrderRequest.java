package com.trading.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    private String idempotencyKey;   // UUID from client — dedup via Redis
    private String symbol;           // e.g. "AAPL", "GOOGL"
    private String side;             // "BUY" or "SELL"
    private String type;             // "LIMIT" or "MARKET"
    private Integer quantity;        // number of shares
    private Double price;            // limit price (null if MARKET order)
    private String userId;           // who is placing the order
}