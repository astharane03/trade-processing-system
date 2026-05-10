package com.trading.order.kafka;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.events.OrderPlacedEvent;
import com.trading.common.events.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        kafkaTemplate.send(KafkaTopics.ORDERS_PLACED, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderPlacedEvent for orderId={} error={}",
                                event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published OrderPlacedEvent orderId={} to topic={}",
                                event.getOrderId(), KafkaTopics.ORDERS_PLACED);
                    }
                });
    }

    public void publishTradeExecuted(TradeExecutedEvent event) {
        kafkaTemplate.send(KafkaTopics.TRADES_EXECUTED, event.getTradeId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TradeExecutedEvent tradeId={} error={}",
                                event.getTradeId(), ex.getMessage());
                    } else {
                        log.info("Published TradeExecutedEvent tradeId={} to topic={}",
                                event.getTradeId(), KafkaTopics.TRADES_EXECUTED);
                    }
                });
    }
}