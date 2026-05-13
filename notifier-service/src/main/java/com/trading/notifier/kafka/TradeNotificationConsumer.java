package com.trading.notifier.kafka;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.events.TradeExecutedEvent;
import com.trading.notifier.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeNotificationConsumer {

    private final NotificationWebSocketHandler webSocketHandler;

    /*
     * Same topic (trades.executed) as portfolio-service consumer
     * but DIFFERENT consumer group (notifier-service-group).
     *
     * Kafka delivers each message to ONE consumer per group.
     * So this consumer gets every trade independently of portfolio-service.
     * Both services process the same event in parallel — decoupled.
     */
    @KafkaListener(
            topics = KafkaTopics.TRADES_EXECUTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload TradeExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received trade notification tradeId={} symbol={} partition={} offset={}",
                event.getTradeId(), event.getSymbol(), partition, offset);

        try {
            /*
             * Every trade has TWO users — notify both.
             * Buyer gets "BUY order filled" message.
             * Seller gets "SELL order filled" message.
             * Both notifications pushed independently.
             */
            webSocketHandler.sendTradeNotification(event.getBuyUserId(), event, "BUY");

            webSocketHandler.sendTradeNotification(event.getSellUserId(), event, "SELL");

            acknowledgment.acknowledge();
            log.info("Acknowledged tradeId={} offset={}", event.getTradeId(), offset);

        } catch (Exception e) {
            log.error("Failed to process trade notification tradeId={} error={}",
                    event.getTradeId(), e.getMessage(), e);
            throw e; // triggers retry → DLQ after 3 attempts
        }
    }
}