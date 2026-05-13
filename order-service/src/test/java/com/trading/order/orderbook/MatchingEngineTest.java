package com.trading.order.orderbook;

import com.trading.common.events.TradeExecutedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private OrderBook orderBook;
    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
        matchingEngine = new MatchingEngine(orderBook);
    }

    // ── no match ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no match when bid price below ask price")
    void noMatch_whenBidBelowAsk() {
        orderBook.addOrder(buildBuy("b1", 148.0, 100, 1000L));
        orderBook.addOrder(buildSell("s1", 150.0, 100, 2000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        assertTrue(trades.isEmpty());
        // both orders still in book
        assertEquals(1, orderBook.getBids("AAPL").size());
        assertEquals(1, orderBook.getAsks("AAPL").size());
    }

    @Test
    @DisplayName("no match when book is empty")
    void noMatch_emptyBook() {
        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");
        assertTrue(trades.isEmpty());
    }

    @Test
    @DisplayName("no match when only bids exist")
    void noMatch_onlyBids() {
        orderBook.addOrder(buildBuy("b1", 150.0, 100, 1000L));
        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");
        assertTrue(trades.isEmpty());
    }

    // ── full match ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("full match when bid price equals ask price")
    void fullMatch_equalPrices() {
        orderBook.addOrder(buildBuy("b1", 150.0, 100, 1000L));
        orderBook.addOrder(buildSell("s1", 150.0, 100, 2000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).getQuantity());
        assertEquals(150.0, trades.get(0).getExecutionPrice());
        // both fully filled — removed from book
        assertTrue(orderBook.getBids("AAPL").isEmpty());
        assertTrue(orderBook.getAsks("AAPL").isEmpty());
    }

    @Test
    @DisplayName("full match when bid price above ask price")
    void fullMatch_bidAboveAsk() {
        orderBook.addOrder(buildBuy("b1", 155.0, 100, 1000L));
        orderBook.addOrder(buildSell("s1", 148.0, 100, 2000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        assertEquals(1, trades.size());
        // execution price = ask price (seller sets price)
        assertEquals(148.0, trades.get(0).getExecutionPrice());
        assertEquals("b1", trades.get(0).getBuyOrderId());
        assertEquals("s1", trades.get(0).getSellOrderId());
    }

    @Test
    @DisplayName("execution price is always ask price not bid price")
    void executionPrice_isAlwaysAskPrice() {
        orderBook.addOrder(buildBuy("b1", 200.0, 50, 1000L));
        orderBook.addOrder(buildSell("s1", 100.0, 50, 2000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        // buyer offered $200, seller asked $100 → executes at $100
        assertEquals(100.0, trades.get(0).getExecutionPrice());
    }

    // ── partial fill ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("partial fill when bid qty greater than ask qty")
    void partialFill_bidQtyGreaterThanAsk() {
        orderBook.addOrder(buildBuy("b1", 150.0, 100, 1000L));
        orderBook.addOrder(buildSell("s1", 148.0, 60, 2000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        assertEquals(1, trades.size());
        assertEquals(60, trades.get(0).getQuantity()); // only 60 matched

        // ask fully filled — removed
        assertTrue(orderBook.getAsks("AAPL").isEmpty());

        // bid partially filled — still in book with 40 remaining
        assertEquals(1, orderBook.getBids("AAPL").size());
        assertEquals(40, orderBook.getBids("AAPL").peek().getRemainingQuantity());
    }

    @Test
    @DisplayName("partial fill when ask qty greater than bid qty")
    void partialFill_askQtyGreaterThanBid() {
        orderBook.addOrder(buildBuy("b1", 150.0, 40, 1000L));
        orderBook.addOrder(buildSell("s1", 148.0, 100, 2000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).getQuantity());

        // bid fully filled
        assertTrue(orderBook.getBids("AAPL").isEmpty());

        // ask partially filled — 60 remaining
        assertEquals(60, orderBook.getAsks("AAPL").peek().getRemainingQuantity());
    }

    // ── multiple matches ─────────────────────────────────────────────────────

    @Test
    @DisplayName("one bid matches multiple asks sequentially")
    void oneBid_matchesMultipleAsks() {
        // bid for 100 shares
        orderBook.addOrder(buildBuy("b1", 160.0, 100, 1000L));
        // two asks totalling 100 shares
        orderBook.addOrder(buildSell("s1", 148.0, 60, 2000L));
        orderBook.addOrder(buildSell("s2", 149.0, 40, 3000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        // two separate trades
        assertEquals(2, trades.size());
        assertEquals(60, trades.get(0).getQuantity()); // s1 first (lower price)
        assertEquals(40, trades.get(1).getQuantity()); // s2 second

        // all orders fully filled
        assertTrue(orderBook.getBids("AAPL").isEmpty());
        assertTrue(orderBook.getAsks("AAPL").isEmpty());
    }

    @Test
    @DisplayName("price-time priority — earlier order matched first at same price")
    void priceTimePriority_earlierOrderMatchedFirst() {
        orderBook.addOrder(buildBuy("b1", 150.0, 100, 5000L)); // later
        orderBook.addOrder(buildBuy("b2", 150.0, 100, 1000L)); // earlier
        orderBook.addOrder(buildSell("s1", 148.0, 100, 9000L));

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        assertEquals(1, trades.size());
        // b2 matched first (same price, earlier timestamp)
        assertEquals("b2", trades.get(0).getBuyOrderId());

        // b1 still in book
        assertEquals(1, orderBook.getBids("AAPL").size());
        assertEquals("b1", orderBook.getBids("AAPL").peek().getOrderId());
    }

    @Test
    @DisplayName("trade event contains correct buyer and seller userIds")
    void tradeEvent_containsCorrectUserIds() {
        orderBook.addOrder(Order.builder()
                .orderId("b1").userId("alice").symbol("AAPL")
                .side("BUY").type("LIMIT")
                .quantity(100).remainingQuantity(100)
                .price(150.0).timestamp(1000L).build());

        orderBook.addOrder(Order.builder()
                .orderId("s1").userId("bob").symbol("AAPL")
                .side("SELL").type("LIMIT")
                .quantity(100).remainingQuantity(100)
                .price(148.0).timestamp(2000L).build());

        List<TradeExecutedEvent> trades = matchingEngine.match("AAPL");

        assertEquals("alice", trades.get(0).getBuyUserId());
        assertEquals("bob", trades.get(0).getSellUserId());
        assertEquals("AAPL", trades.get(0).getSymbol());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Order buildBuy(String id, double price, int qty, long timestamp) {
        return Order.builder()
                .orderId(id).userId("buyer-" + id)
                .symbol("AAPL").side("BUY").type("LIMIT")
                .quantity(qty).remainingQuantity(qty)
                .price(price).timestamp(timestamp).build();
    }

    private Order buildSell(String id, double price, int qty, long timestamp) {
        return Order.builder()
                .orderId(id).userId("seller-" + id)
                .symbol("AAPL").side("SELL").type("LIMIT")
                .quantity(qty).remainingQuantity(qty)
                .price(price).timestamp(timestamp).build();
    }
}