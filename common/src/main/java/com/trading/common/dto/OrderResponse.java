package com.trading.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String orderId;
    private String status;       // "ACCEPTED", "DUPLICATE", "MATCHED"
    private String message;
    private String symbol;
    private String side;
    private Integer quantity;
    private Double price;
    private String userId;
    private long timestamp;
}