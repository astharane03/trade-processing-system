package com.trading.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.dto.OrderRequest;
import com.trading.common.dto.OrderResponse;
import com.trading.common.events.OrderPlacedEvent;
import com.trading.common.events.TradeExecutedEvent;
import com.trading.order.entity.EventStoreEntity;
import com.trading.order.entity.OrderEntity;
import com.trading.order.kafka.OrderEventPublisher;
import com.trading.order.orderbook.MatchingEngine;
import com.trading.order.orderbook.Order;
import com.trading.order.orderbook.OrderBook;
import com.trading.order.repository.EventStoreRepository;
import com.trading.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventStoreRepository eventStoreRepository;
    private final IdempotencyService idempotencyService;
    private final OrderBook orderBook;
    private final MatchingEngine matchingEngine;
    private final OrderEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {

        // ── Step 1: Idempotency check ──────────────────────────────────────
        if (!idempotencyService.isFirstRequest(request.getIdempotencyKey())) {
            String existingOrderId = idempotencyService.getOrderId(
                    request.getIdempotencyKey());
            log.warn("Duplicate order rejected idempotencyKey={}",
                    request.getIdempotencyKey());
            return OrderResponse.builder()
                    .orderId(existingOrderId)
                    .status("DUPLICATE")
                    .message("Order already processed")
                    .build();
        }

        // ── Step 2: Create order entity ────────────────────────────────────
        String orderId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        OrderEntity orderEntity = OrderEntity.builder()
                .orderId(orderId)
                .idempotencyKey(request.getIdempotencyKey())
                .userId(request.getUserId())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(request.getType())
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .price(request.getPrice())
                .status("PENDING")
                .createdAt(now)
                .build();

        // ── Step 3: Persist order to DB ────────────────────────────────────
        orderRepository.save(orderEntity);
        log.info("Order saved to DB orderId={}", orderId);

        // ── Step 4: Append to Event Store ──────────────────────────────────
        OrderPlacedEvent orderPlacedEvent = OrderPlacedEvent.builder()
                .orderId(orderId)
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(request.getType())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .userId(request.getUserId())
                .timestamp(now)
                .build();

        saveToEventStore("ORDER_PLACED", orderId, orderPlacedEvent);

        // ── Step 5: Add to in-memory Order Book ────────────────────────────
        Order order = Order.builder()
                .orderId(orderId)
                .userId(request.getUserId())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(request.getType())
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .price(request.getPrice())
                .timestamp(now)
                .build();

        orderBook.addOrder(order);
        log.info("Order added to OrderBook orderId={} side={} symbol={}",
                orderId, request.getSide(), request.getSymbol());

        // ── Step 6: Publish OrderPlaced to Kafka ───────────────────────────
        eventPublisher.publishOrderPlaced(orderPlacedEvent);

        // ── Step 7: Run Matching Engine ────────────────────────────────────
        List<TradeExecutedEvent> trades = matchingEngine.match(request.getSymbol());

        // ── Step 8: Process each trade ─────────────────────────────────────
        for (TradeExecutedEvent trade : trades) {

            // update order statuses in DB
            updateOrderStatus(trade.getBuyOrderId(), trade.getQuantity());
            updateOrderStatus(trade.getSellOrderId(), trade.getQuantity());

            // append trade to event store
            saveToEventStore("TRADE_EXECUTED", trade.getTradeId(), trade);

            // publish trade to Kafka
            eventPublisher.publishTradeExecuted(trade);

            log.info("Trade executed tradeId={} symbol={} qty={} price={}",
                    trade.getTradeId(), trade.getSymbol(),
                    trade.getQuantity(), trade.getExecutionPrice());
        }

        // ── Step 9: Mark idempotency key as completed ──────────────────────
        idempotencyService.markCompleted(request.getIdempotencyKey(), orderId);

        return OrderResponse.builder()
                .orderId(orderId)
                .status(trades.isEmpty() ? "ACCEPTED" : "MATCHED")
                .message(trades.isEmpty()
                        ? "Order placed in book"
                        : "Order matched — " + trades.size() + " trade(s) executed")
                .symbol(request.getSymbol())
                .side(request.getSide())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .userId(request.getUserId())
                .timestamp(now)
                .build();
    }

    private void updateOrderStatus(String orderId, int matchedQty) {
        orderRepository.findById(orderId).ifPresent(order -> {
            int newRemaining = order.getRemainingQuantity() - matchedQty;
            order.setRemainingQuantity(newRemaining);
            order.setStatus(newRemaining == 0 ? "FILLED" : "PARTIALLY_FILLED");
            orderRepository.save(order);
        });
    }

    private void saveToEventStore(String eventType, String aggregateId, Object payload) {
        try {
            EventStoreEntity event = EventStoreEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(System.currentTimeMillis())
                    .build();
            eventStoreRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event type={} aggregateId={}",
                    eventType, aggregateId, e);
            throw new RuntimeException("Event store write failed", e);
        }
    }
}