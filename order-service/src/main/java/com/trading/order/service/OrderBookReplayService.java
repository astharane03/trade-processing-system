package com.trading.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.events.OrderPlacedEvent;
import com.trading.order.entity.EventStoreEntity;
import com.trading.order.orderbook.Order;
import com.trading.order.orderbook.OrderBook;
import com.trading.order.repository.EventStoreRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookReplayService {

    private final EventStoreRepository eventStoreRepository;
    private final OrderBook orderBook;
    private final ObjectMapper objectMapper;

    /*
     * On restart, OrderBook heap is empty (it's in RAM).
     * We replay all ORDER_PLACED events to refill the heap.
     * But we skip orders that are FILLED or PARTIALLY_FILLED enough
     * by checking remaining_quantity from the orders table.
     *
     * Simpler approach: replay ORDER_PLACED, then remove FILLED orders
     * by replaying TRADE_EXECUTED events and reducing quantities.
     */
    @PostConstruct
    public void rebuildOrderBook() {
        log.info("Rebuilding in-memory OrderBook from event store...");

        // Step 1: load all ORDER_PLACED events
        List<EventStoreEntity> placedEvents = eventStoreRepository
                .findByEventTypeOrderByCreatedAtAsc("ORDER_PLACED");

        for (EventStoreEntity stored : placedEvents) {
            try {
                OrderPlacedEvent event = objectMapper.readValue(
                        stored.getPayload(), OrderPlacedEvent.class);

                Order order = Order.builder()
                        .orderId(event.getOrderId())
                        .userId(event.getUserId())
                        .symbol(event.getSymbol())
                        .side(event.getSide())
                        .type(event.getType())
                        .quantity(event.getQuantity())
                        .remainingQuantity(event.getQuantity()) // will reduce below
                        .price(event.getPrice())
                        .timestamp(event.getTimestamp())
                        .build();

                orderBook.addOrder(order);

            } catch (Exception e) {
                log.error("Failed to replay ORDER_PLACED id={}", stored.getId(), e);
            }
        }

        // Step 2: replay TRADE_EXECUTED to reduce/remove filled orders
        List<EventStoreEntity> tradeEvents = eventStoreRepository
                .findByEventTypeOrderByCreatedAtAsc("TRADE_EXECUTED");

        for (EventStoreEntity stored : tradeEvents) {
            try {
                // parse just enough to get order IDs and quantities
                var node = objectMapper.readTree(stored.getPayload());
                String buyOrderId  = node.get("buyOrderId").asText();
                String sellOrderId = node.get("sellOrderId").asText();
                String symbol      = node.get("symbol").asText();
                int qty            = node.get("quantity").asInt();

                reduceOrRemove(symbol, "BUY",  buyOrderId,  qty);
                reduceOrRemove(symbol, "SELL", sellOrderId, qty);

            } catch (Exception e) {
                log.error("Failed to replay TRADE_EXECUTED id={}", stored.getId(), e);
            }
        }

        log.info("OrderBook rebuild complete.");
    }

    private void reduceOrRemove(String symbol, String side,
                                String orderId, int matchedQty) {
        // get the correct heap
        var heap = "BUY".equals(side)
                ? orderBook.getBids(symbol)
                : orderBook.getAsks(symbol);

        heap.stream()
                .filter(o -> o.getOrderId().equals(orderId))
                .findFirst()
                .ifPresent(o -> {
                    int newRemaining = o.getRemainingQuantity() - matchedQty;
                    if (newRemaining <= 0) {
                        heap.remove(o); // fully filled — remove from heap
                    } else {
                        o.setRemainingQuantity(newRemaining); // partial fill — reduce
                    }
                });
    }
}