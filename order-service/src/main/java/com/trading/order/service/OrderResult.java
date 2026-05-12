package com.trading.order.service;

import com.trading.common.dto.OrderResponse;
import com.trading.common.events.OrderPlacedEvent;
import com.trading.common.events.TradeExecutedEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OrderResult {
    private final String orderId;
    private final OrderPlacedEvent orderPlacedEvent;
    private final List<TradeExecutedEvent> trades;
    private final OrderResponse response;
}