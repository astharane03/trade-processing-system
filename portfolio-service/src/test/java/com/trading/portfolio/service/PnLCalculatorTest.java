package com.trading.portfolio.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PnLCalculatorTest {

    private PnLCalculator calculator;
    private static final double DELTA = 0.01; // tolerance for double comparison

    @BeforeEach
    void setUp() {
        calculator = new PnLCalculator();
    }

    // ── calculateNewAvgCost ──────────────────────────────────────────────────

    @Test
    @DisplayName("avg cost when buying into empty position")
    void avgCost_firstBuy() {
        // no existing position
        double avgCost = calculator.calculateNewAvgCost(0, 0.0, 100, 150.0);
        assertEquals(150.0, avgCost, DELTA);
    }

    @Test
    @DisplayName("avg cost when adding to existing position at different price")
    void avgCost_addToExistingPosition() {
        // own 100 @ $140, buy 50 more @ $160
        // expected = (100*140 + 50*160) / 150 = 22000/150 = $146.67
        double avgCost = calculator.calculateNewAvgCost(100, 140.0, 50, 160.0);
        assertEquals(146.67, avgCost, DELTA);
    }

    @Test
    @DisplayName("avg cost unchanged when buying at same price")
    void avgCost_buyAtSamePrice() {
        double avgCost = calculator.calculateNewAvgCost(100, 150.0, 50, 150.0);
        assertEquals(150.0, avgCost, DELTA);
    }

    @Test
    @DisplayName("avg cost when buying at lower price reduces average")
    void avgCost_buyAtLowerPrice_reducesAverage() {
        // own 100 @ $160, buy 100 more @ $140
        // expected = (100*160 + 100*140) / 200 = 30000/200 = $150
        double avgCost = calculator.calculateNewAvgCost(100, 160.0, 100, 140.0);
        assertEquals(150.0, avgCost, DELTA);
    }

    // ── calculateRealizedPnL ─────────────────────────────────────────────────

    @Test
    @DisplayName("realized PnL is positive when selling above avg cost")
    void realizedPnl_profit() {
        // avgCost=$140, sell 60 @ $150 → profit = (150-140)*60 = $600
        double pnl = calculator.calculateRealizedPnL(140.0, 150.0, 60);
        assertEquals(600.0, pnl, DELTA);
    }

    @Test
    @DisplayName("realized PnL is negative when selling below avg cost")
    void realizedPnl_loss() {
        // avgCost=$150, sell 60 @ $140 → loss = (140-150)*60 = -$600
        double pnl = calculator.calculateRealizedPnL(150.0, 140.0, 60);
        assertEquals(-600.0, pnl, DELTA);
    }

    @Test
    @DisplayName("realized PnL is zero when selling at avg cost")
    void realizedPnl_breakEven() {
        double pnl = calculator.calculateRealizedPnL(150.0, 150.0, 100);
        assertEquals(0.0, pnl, DELTA);
    }

    @Test
    @DisplayName("realized PnL scales linearly with quantity")
    void realizedPnl_scalesWithQuantity() {
        double pnl10 = calculator.calculateRealizedPnL(140.0, 150.0, 10);
        double pnl100 = calculator.calculateRealizedPnL(140.0, 150.0, 100);
        assertEquals(pnl10 * 10, pnl100, DELTA);
    }

    // ── calculateUnrealizedPnL ───────────────────────────────────────────────

    @Test
    @DisplayName("unrealized PnL is positive when market above avg cost")
    void unrealizedPnl_profit() {
        // hold 100 @ $140 avg, market = $155
        // unrealized = (155-140)*100 = $1500
        double pnl = calculator.calculateUnrealizedPnL(140.0, 155.0, 100);
        assertEquals(1500.0, pnl, DELTA);
    }

    @Test
    @DisplayName("unrealized PnL is negative when market below avg cost")
    void unrealizedPnl_loss() {
        // hold 100 @ $150 avg, market = $130
        // unrealized = (130-150)*100 = -$2000
        double pnl = calculator.calculateUnrealizedPnL(150.0, 130.0, 100);
        assertEquals(-2000.0, pnl, DELTA);
    }

    @Test
    @DisplayName("unrealized PnL is zero when market equals avg cost")
    void unrealizedPnl_breakEven() {
        double pnl = calculator.calculateUnrealizedPnL(150.0, 150.0, 100);
        assertEquals(0.0, pnl, DELTA);
    }

    @Test
    @DisplayName("unrealized PnL is zero when holding zero shares")
    void unrealizedPnl_zeroShares() {
        double pnl = calculator.calculateUnrealizedPnL(150.0, 200.0, 0);
        assertEquals(0.0, pnl, DELTA);
    }

    // ── combined scenario ────────────────────────────────────────────────────

    @Test
    @DisplayName("full scenario: buy twice then partial sell")
    void fullScenario_buyTwiceThenPartialSell() {
        // trade 1: buy 100 @ $140
        double avgCost = calculator.calculateNewAvgCost(0, 0.0, 100, 140.0);
        assertEquals(140.0, avgCost, DELTA);

        // trade 2: buy 50 more @ $160
        avgCost = calculator.calculateNewAvgCost(100, avgCost, 50, 160.0);
        assertEquals(146.67, avgCost, DELTA); // weighted average

        // trade 3: sell 60 @ $155
        double realized = calculator.calculateRealizedPnL(avgCost, 155.0, 60);
        assertEquals(500.0, realized, DELTA);

        // remaining 90 shares unrealized at market $155
        double unrealized = calculator.calculateUnrealizedPnL(avgCost, 155.0, 90);
        assertTrue(unrealized > 0); // still profitable on remaining
    }
}