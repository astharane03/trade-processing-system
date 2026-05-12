package com.trading.portfolio.kafka;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.events.PortfolioUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPortfolioUpdated(PortfolioUpdatedEvent event) {
        kafkaTemplate.send(
                        KafkaTopics.PORTFOLIO_UPDATES,
                        event.getUserId(),      // key = userId → same user always same partition
                        event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PortfolioUpdatedEvent userId={} error={}",
                                event.getUserId(), ex.getMessage());
                    } else {
                        log.info("Published PortfolioUpdatedEvent userId={} symbol={}",
                                event.getUserId(), event.getSymbol());
                    }
                });
    }
}