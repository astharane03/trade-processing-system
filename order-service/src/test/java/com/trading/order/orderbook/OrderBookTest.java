package com.trading.order.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
    }

    // ── BID heap ordering ────────────────────────────────────────────────────

    @Test
    @DisplayName("BUY orders sorted by highest price first (max-heap)")
    void bids_sortedByHighestPriceFirst() {
        orderBook.addOrder(buildOrder("b1", "BUY", 140.0, 1000L));
        orderBook.addOrder(buildOrder("b2", "BUY", 155.0, 2000L));
        orderBook.addOrder(buildOrder("b3", "BUY", 148.0, 3000L));

        PriorityQueue<Order> bids = orderBook.getBids("AAPL");

        // highest price must be at top
        assertEquals(155.0, bids.peek().getPrice());
    }

    @Test
    @DisplayName("BUY orders with same price sorted by earliest timestamp first")
    void bids_samePriceSortedByTimestamp() {
        orderBook.addOrder(buildOrder("b1", "BUY", 150.0, 3000L)); // later
        orderBook.addOrder(buildOrder("b2", "BUY", 150.0, 1000L)); // earlier
        orderBook.addOrder(buildOrder("b3", "BUY", 150.0, 2000L)); // middle

        PriorityQueue<Order> bids = orderBook.getBids("AAPL");

        // earliest timestamp wins tiebreaker (price-time priority)
        assertEquals("b2", bids.peek().getOrderId());
    }

    // ── ASK heap ordering ────────────────────────────────────────────────────

    @Test
    @DisplayName("SELL orders sorted by lowest price first (min-heap)")
    void asks_sortedByLowestPriceFirst() {
        orderBook.addOrder(buildOrder("s1", "SELL", 155.0, 1000L));
        orderBook.addOrder(buildOrder("s2", "SELL", 148.0, 2000L));
        orderBook.addOrder(buildOrder("s3", "SELL", 160.0, 3000L));

        PriorityQueue<Order> asks = orderBook.getAsks("AAPL");

        // lowest price must be at top
        assertEquals(148.0, asks.peek().getPrice());
    }

    @Test
    @DisplayName("SELL orders with same price sorted by earliest timestamp first")
    void asks_samePriceSortedByTimestamp() {
        orderBook.addOrder(buildOrder("s1", "SELL", 150.0, 5000L));
        orderBook.addOrder(buildOrder("s2", "SELL", 150.0, 1000L));
        orderBook.addOrder(buildOrder("s3", "SELL", 150.0, 3000L));

        PriorityQueue<Order> asks = orderBook.getAsks("AAPL");

        assertEquals("s2", asks.peek().getOrderId());
    }

    // ── Symbol isolation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Orders for different symbols do not interfere")
    void differentSymbols_isolatedHeaps() {
        orderBook.addOrder(buildOrderForSymbol("b1", "AAPL", "BUY", 150.0));
        orderBook.addOrder(buildOrderForSymbol("b2", "GOOGL", "BUY", 200.0));

        assertEquals(1, orderBook.getBids("AAPL").size());
        assertEquals(1, orderBook.getBids("GOOGL").size());
        assertTrue(orderBook.getAsks("AAPL").isEmpty());
    }

    // ── removeOrder ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeOrder removes correct order from heap")
    void removeOrder_removesCorrectOrder() {
        orderBook.addOrder(buildOrder("b1", "BUY", 150.0, 1000L));
        orderBook.addOrder(buildOrder("b2", "BUY", 155.0, 2000L));

        orderBook.removeOrder("AAPL", "BUY", "b2");

        PriorityQueue<Order> bids = orderBook.getBids("AAPL");
        assertEquals(1, bids.size());
        assertEquals("b1", bids.peek().getOrderId());
    }

    @Test
    @DisplayName("empty book returns empty heap not null")
    void emptyBook_returnsEmptyHeap() {
        PriorityQueue<Order> bids = orderBook.getBids("AAPL");
        assertNotNull(bids);
        assertTrue(bids.isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Order buildOrder(String id, String side, double price, long timestamp) {
        return Order.builder()
                .orderId(id)
                .userId("user-1")
                .symbol("AAPL")
                .side(side)
                .type("LIMIT")
                .quantity(100)
                .remainingQuantity(100)
                .price(price)
                .timestamp(timestamp)
                .build();
    }

    private Order buildOrderForSymbol(String id, String symbol,
                                      String side, double price) {
        return Order.builder()
                .orderId(id)
                .userId("user-1")
                .symbol(symbol)
                .side(side)
                .type("LIMIT")
                .quantity(100)
                .remainingQuantity(100)
                .price(price)
                .timestamp(1000L)
                .build();
    }
}