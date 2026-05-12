package com.trading.portfolio.kafka;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.events.TradeExecutedEvent;
import com.trading.portfolio.service.PortfolioService;
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
public class TradeEventConsumer {

    private final PortfolioService portfolioService;

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

        log.info("Consumed TradeExecutedEvent tradeId={} partition={} offset={}",
                event.getTradeId(), partition, offset);

        try {
            portfolioService.processTrade(event);
            // manual ack — only commit offset after successful processing
            acknowledgment.acknowledge();
            log.info("Acknowledged tradeId={} offset={}", event.getTradeId(), offset);

        } catch (Exception e) {
            log.error("Failed to process tradeId={} error={}",
                    event.getTradeId(), e.getMessage(), e);
            // do NOT acknowledge → Kafka will redeliver
            // after max retries → goes to DLQ (configured in KafkaConfig)
            throw e;
        }
    }
}