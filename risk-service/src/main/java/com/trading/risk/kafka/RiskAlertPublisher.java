package com.trading.risk.kafka;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.events.RiskAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAlertPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAlert(RiskAlertEvent alert) {
        kafkaTemplate.send(
                        KafkaTopics.RISK_ALERTS,
                        alert.getUserId(),   // key = userId → same user, same partition
                        alert)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish RiskAlertEvent alertId={} error={}",
                                alert.getAlertId(), ex.getMessage());
                    } else {
                        log.info("Published RiskAlertEvent alertId={} type={} userId={}",
                                alert.getAlertId(), alert.getAlertType(), alert.getUserId());
                    }
                });
    }
}