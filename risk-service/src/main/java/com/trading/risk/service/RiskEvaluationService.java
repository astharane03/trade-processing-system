package com.trading.risk.service;

import com.trading.common.events.PortfolioUpdatedEvent;
import com.trading.common.events.RiskAlertEvent;
import com.trading.risk.kafka.RiskAlertPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEvaluationService {

    private final VaRCalculator varCalculator;
    private final RiskAlertPublisher alertPublisher;

    /*
     * POSITION LIMIT — max shares one user can hold in one symbol.
     * Configurable via application.properties.
     * Real exchanges use notional value limits (dollar amount), not share count.
     * For interview purposes, share count is clear and explainable.
     */
    @Value("${risk.position.limit:1000}")
    private int positionLimit;

    /*
     * VAR THRESHOLD — max acceptable daily loss in dollars.
     * If VaR exceeds this, user is over-leveraged for their position size.
     */
    @Value("${risk.var.threshold:10000.0}")
    private double varThreshold;

    public void evaluate(PortfolioUpdatedEvent event) {
        log.info("Evaluating risk for userId={} symbol={} qty={}",
                event.getUserId(), event.getSymbol(), event.getNewQuantity());

        checkPositionLimit(event);
        checkVaRBreach(event);
    }

    // ── Check 1: position limit ──────────────────────────────────────────────

    private void checkPositionLimit(PortfolioUpdatedEvent event) {
        int currentQty = event.getNewQuantity();

        if (currentQty > positionLimit) {
            log.warn("POSITION LIMIT BREACH userId={} symbol={} qty={} limit={}",
                    event.getUserId(), event.getSymbol(), currentQty, positionLimit);

            RiskAlertEvent alert = RiskAlertEvent.builder()
                    .alertId(UUID.randomUUID().toString())
                    .userId(event.getUserId())
                    .alertType("POSITION_LIMIT_BREACH")
                    .symbol(event.getSymbol())
                    .currentValue((double) currentQty)
                    .limitValue((double) positionLimit)
                    .message(String.format(
                            "Position limit breached: holding %d shares of %s, limit is %d",
                            currentQty, event.getSymbol(), positionLimit))
                    .timestamp(System.currentTimeMillis())
                    .build();

            alertPublisher.publishAlert(alert);
        }
    }

    // ── Check 2: VaR breach ──────────────────────────────────────────────────

    private void checkVaRBreach(PortfolioUpdatedEvent event) {
        /*
         * positionValue = qty * averageCost
         * This is the total capital at risk for this position.
         * VaR is then calculated on this position value.
         */
        double positionValue = event.getNewQuantity() * event.getAverageCost();
        double var = varCalculator.calculate(positionValue);

        log.info("VaR calculated userId={} symbol={} positionValue={} VaR={}",
                event.getUserId(), event.getSymbol(), positionValue, var);

        if (var > varThreshold) {
            log.warn("VAR BREACH userId={} symbol={} VaR={} threshold={}",
                    event.getUserId(), event.getSymbol(), var, varThreshold);

            RiskAlertEvent alert = RiskAlertEvent.builder()
                    .alertId(UUID.randomUUID().toString())
                    .userId(event.getUserId())
                    .alertType("VAR_BREACH")
                    .symbol(event.getSymbol())
                    .currentValue(var)
                    .limitValue(varThreshold)
                    .message(String.format(
                            "VaR breach: 1-day 95%% VaR of $%.2f exceeds threshold of $%.2f for %s",
                            var, varThreshold, event.getSymbol()))
                    .timestamp(System.currentTimeMillis())
                    .build();

            alertPublisher.publishAlert(alert);
        }
    }
}