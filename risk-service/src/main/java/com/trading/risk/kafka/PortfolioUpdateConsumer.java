package com.trading.risk.kafka;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.events.PortfolioUpdatedEvent;
import com.trading.risk.service.RiskEvaluationService;
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
public class PortfolioUpdateConsumer {

    private final RiskEvaluationService riskEvaluationService;

    @KafkaListener(
            topics = KafkaTopics.PORTFOLIO_UPDATES,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload PortfolioUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received PortfolioUpdatedEvent userId={} symbol={} partition={} offset={}",
                event.getUserId(), event.getSymbol(), partition, offset);

        try {
            riskEvaluationService.evaluate(event);
            acknowledgment.acknowledge();
            log.info("Acknowledged portfolio update userId={} offset={}",
                    event.getUserId(), offset);
        } catch (Exception e) {
            log.error("Risk evaluation failed userId={} error={}",
                    event.getUserId(), e.getMessage(), e);
            throw e; // triggers retry → DLQ after 3 attempts
        }
    }
}