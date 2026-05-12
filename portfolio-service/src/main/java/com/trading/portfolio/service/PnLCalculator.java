package com.trading.portfolio.service;

import org.springframework.stereotype.Component;

//avgCost = average price paid per share across ALL buy trades.
@Component
public class PnLCalculator {

    public double calculateRealizedPnL(
            double avgCost,
            double executionPrice,
            int quantitySold) {

        return (executionPrice - avgCost) * quantitySold;
    }

    /*
     * UNREALIZED P&L — paper profit/loss on shares still held
     *
     * Formula:
     *   unrealizedPnL = (currentMarketPrice - avgCost) * quantityHeld
     *
     * Example:
     *   Hold 40 AAPL, avgCost=$140, market price=$155
     *   unrealizedPnL = (155 - 140) * 40 = +$600 (not locked in yet)
     *
     * NOTE: we use last executionPrice as proxy for market price.
     * In prod this would come from a live market data feed.
     */
    public double calculateUnrealizedPnL(
            double avgCost,
            double currentMarketPrice,
            int quantityHeld) {

        return (currentMarketPrice - avgCost) * quantityHeld;
    }

    /*
     * NEW AVERAGE COST after a BUY — weighted average
     *
     * Formula:
     *   newAvgCost = (existingQty * existingAvgCost + newQty * newPrice)
     *                / (existingQty + newQty)
     *
     * Example:
     *   Own 100 AAPL at $140 avg
     *   Buy 50 more at $160
     *   newAvgCost = (100*140 + 50*160) / 150
     *              = (14000 + 8000) / 150
     *              = $146.67
     */
    public double calculateNewAvgCost(
            int existingQty,
            double existingAvgCost,
            int newQty,
            double newPrice) {

        return ((existingQty * existingAvgCost) + (newQty * newPrice))
                / (existingQty + newQty);
    }
}