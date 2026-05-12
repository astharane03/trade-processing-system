package com.trading.risk.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VaRCalculator {

    /*
     * HISTORICAL SIMULATION VaR — simplest production-viable method
     *
     * VaR (Value at Risk) answers:
     *   "What is the maximum loss I should expect with 95% confidence?"
     *
     * HOW IT WORKS:
     *   1. Take a list of daily P&L returns (e.g. last 100 days)
     *   2. Sort them ascending (worst first)
     *   3. VaR at 95% confidence = the loss at the 5th percentile
     *      i.e. the 5th worst day out of 100
     *
     * EXAMPLE:
     *   returns = [-500, -300, -200, -150, -100, -50, 0, +50, +100, ...]
     *   sorted ascending, 5th percentile of 100 = index 4 = -100
     *   VaR = $100 (we expect to lose at most $100 on 95 out of 100 days)
     *
     * For this project we simulate returns using:
     *   positionValue * dailyVolatility for N simulated days
     * This is acceptable for interview-level code — in prod you'd use
     * actual historical price data from a market data feed.
     */

    private static final double CONFIDENCE_LEVEL = 0.95;
    private static final int SIMULATION_DAYS = 100;
    private static final double DAILY_VOLATILITY = 0.02; // 2% daily vol assumption

    public double calculate(double positionValue) {
        if (positionValue <= 0) return 0.0;

        // simulate 100 daily P&L scenarios
        List<Double> simulatedReturns = simulateDailyReturns(positionValue);

        // sort ascending — worst losses first
        simulatedReturns.sort(Double::compareTo);

        // 5th percentile index for 95% confidence
        int varIndex = (int) Math.floor((1 - CONFIDENCE_LEVEL) * SIMULATION_DAYS);

        double var = Math.abs(simulatedReturns.get(varIndex));

        return Math.round(var * 100.0) / 100.0; // round to 2 decimal places
    }

    private List<Double> simulateDailyReturns(double positionValue) {
        // deterministic simulation — same position always gives same VaR
        // in prod: replace with actual historical returns from market data service
        List<Double> returns = new java.util.ArrayList<>();
        for (int i = 0; i < SIMULATION_DAYS; i++) {
            // simulate return as sine wave — gives spread of positive/negative returns
            double dailyReturn = positionValue * DAILY_VOLATILITY
                    * Math.sin(i * Math.PI / 10);
            returns.add(dailyReturn);
        }
        return returns;
    }
}