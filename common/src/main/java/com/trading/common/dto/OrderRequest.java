package com.trading.common.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;

    @NotBlank(message = "symbol is required")
    private String symbol;

    @NotBlank(message = "side is required — BUY or SELL")
    @Pattern(regexp = "^(BUY|SELL)$", message = "side must be BUY or SELL")
    private String side;

    @NotBlank(message = "type is required — LIMIT or MARKET")
    @Pattern(regexp = "^(LIMIT|MARKET)$", message = "type must be LIMIT or MARKET")
    private String type;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.01", message = "price must be greater than 0")
    private Double price;

    @NotBlank(message = "userId is required")
    private String userId;
}