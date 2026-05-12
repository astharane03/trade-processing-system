package com.trading.portfolio.service;

import com.trading.common.events.PortfolioUpdatedEvent;
import com.trading.common.events.TradeExecutedEvent;
import com.trading.portfolio.entity.HoldingEntity;
import com.trading.portfolio.entity.PnLEntity;
import com.trading.portfolio.entity.ProcessedEventEntity;
import com.trading.portfolio.kafka.PortfolioEventPublisher;
import com.trading.portfolio.repository.HoldingRepository;
import com.trading.portfolio.repository.PnLRepository;
import com.trading.portfolio.repository.ProcessedEventRepository;
import com.trading.portfolio.websocket.PortfolioWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final ProcessedEventRepository processedEventRepository;
    private final HoldingRepository holdingRepository;
    private final PnLRepository pnLRepository;
    private final PnLCalculator pnLCalculator;
    private final PortfolioEventPublisher eventPublisher;
    private final PortfolioWebSocketHandler webSocketHandler;

    /*
     * Called for EVERY TradeExecutedEvent consumed from Kafka.
     * Two users involved in every trade:
     *   - buyer  → holdings increase
     *   - seller → holdings decrease, realized P&L locked in
     */
    @Transactional
    public void processTrade(TradeExecutedEvent event) {

        // IDEMPOTENCY CHECK — skip if already processed
        if (processedEventRepository.existsById(event.getTradeId())) {
            log.warn("Skipping already-processed tradeId={}", event.getTradeId());
            return;
        }

        log.info("Processing trade tradeId={} symbol={} qty={} price={}",
                event.getTradeId(), event.getSymbol(),
                event.getQuantity(), event.getExecutionPrice());

        // process both sides of trade
        processBuyerSide(event);
        processSellerSide(event);

        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(event.getTradeId())
                .processedAt(System.currentTimeMillis())
                .build());
    }

    // ── Buyer side ─────────────────────────────────────────────────────────

    private void processBuyerSide(TradeExecutedEvent event) {
        String userId = event.getBuyUserId();
        String symbol = event.getSymbol();
        int qty = event.getQuantity();
        double price = event.getExecutionPrice();

        HoldingEntity holding = holdingRepository
                .findByUserIdAndSymbol(userId, symbol)
                .orElse(HoldingEntity.builder()
                        .userId(userId)
                        .symbol(symbol)
                        .quantity(0)
                        .avgCost(0.0)
                        .totalInvested(0.0)
                        .unrealizedPnl(0.0)
                        .build());

        // calculate new weighted average cost
        double newAvgCost = pnLCalculator.calculateNewAvgCost(
                holding.getQuantity(), holding.getAvgCost(), qty, price);

        int newQty = holding.getQuantity() + qty;
        double newTotalInvested = newQty * newAvgCost;
        double unrealizedPnl = pnLCalculator.calculateUnrealizedPnL(newAvgCost, price, newQty);

        holding.setQuantity(newQty);
        holding.setAvgCost(newAvgCost);
        holding.setTotalInvested(newTotalInvested);
        holding.setUnrealizedPnl(unrealizedPnl);
        holding.setLastUpdated(System.currentTimeMillis());

        holdingRepository.save(holding);
        log.info("Buyer holding updated userId={} symbol={} newQty={} avgCost={}",
                userId, symbol, newQty, newAvgCost);

        // save P&L record (realized = 0 for buyer — they haven't sold)
        savePnLRecord(userId, symbol, event.getTradeId(),
                0.0, qty, price, "BUY", event.getTimestamp());

        publishAndNotify(userId, symbol, newQty, newAvgCost, 0.0, unrealizedPnl);
    }

    // ── Seller side ────────────────────────────────────────────────────────

    private void processSellerSide(TradeExecutedEvent event) {
        String userId = event.getSellUserId();
        String symbol = event.getSymbol();
        int qty = event.getQuantity();
        double price = event.getExecutionPrice();

        HoldingEntity holding = holdingRepository
                .findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new IllegalStateException(
                        "Seller has no holdings for symbol=" + symbol
                                + " userId=" + userId));

        // lock in realized P&L
        double realizedPnl = pnLCalculator.calculateRealizedPnL(holding.getAvgCost(), price, qty);

        int newQty = holding.getQuantity() - qty;
        double unrealizedPnl = newQty > 0
                ? pnLCalculator.calculateUnrealizedPnL(holding.getAvgCost(), price, newQty)
                : 0.0;

        holding.setQuantity(newQty);
        holding.setTotalInvested(newQty * holding.getAvgCost());
        holding.setUnrealizedPnl(unrealizedPnl);
        holding.setLastUpdated(System.currentTimeMillis());

        holdingRepository.save(holding);
        log.info("Seller holding updated userId={} symbol={} newQty={} realizedPnL={}",
                userId, symbol, newQty, realizedPnl);

        savePnLRecord(userId, symbol, event.getTradeId(),
                realizedPnl, qty, price, "SELL", event.getTimestamp());

        publishAndNotify(userId, symbol, newQty,
                holding.getAvgCost(), realizedPnl, unrealizedPnl);
    }

    // ── Shared helpers ─────────────────────────────────────────────────────

    private void savePnLRecord(String userId, String symbol, String tradeId,
                               double realizedPnl, int qty, double price,
                               String side, long timestamp) {

        PnLEntity pnl = PnLEntity.builder()
                .userId(userId)
                .symbol(symbol)
                .tradeId(tradeId)
                .realizedPnl(realizedPnl)
                .quantityTraded(qty)
                .executionPrice(price)
                .side(side)
                .timestamp(timestamp)
                .build();

        pnLRepository.save(pnl);
    }

    private void publishAndNotify(String userId, String symbol,
                                  int newQty, double avgCost,
                                  double realizedPnl, double unrealizedPnl) {

        PortfolioUpdatedEvent event = PortfolioUpdatedEvent.builder()
                .userId(userId)
                .symbol(symbol)
                .newQuantity(newQty)
                .averageCost(avgCost)
                .realizedPnL(realizedPnl)
                .unrealizedPnL(unrealizedPnl)
                .timestamp(System.currentTimeMillis())
                .build();

        // publish to Kafka → risk-service listens
        eventPublisher.publishPortfolioUpdated(event);

        // push to WebSocket → browser sees update instantly
        webSocketHandler.sendPortfolioUpdate(userId, event);
    }
}