package com.trading.order.orderbook;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderBook {

    /*
     * BIDS (BUY orders) = MAX-HEAP
     * Highest price at top → best buyer always peek()
     * Tiebreaker = earliest timestamp (FIFO = price-time priority)
     *
     * ASKS (SELL orders) = MIN-HEAP
     * Lowest price at top → best seller always peek()
     * Tiebreaker = earliest timestamp
     */

    // symbol → heap of buy orders
    private final Map<String, PriorityQueue<Order>> bids = new ConcurrentHashMap<>();

    // symbol → heap of sell orders
    private final Map<String, PriorityQueue<Order>> asks = new ConcurrentHashMap<>();

    // BUY comparator: higher price first, then earlier time first
    private static final Comparator<Order> BID_COMPARATOR =
            Comparator.comparingDouble(Order::getPrice).reversed()
                    .thenComparingLong(Order::getTimestamp);

    // SELL comparator: lower price first, then earlier time first
    private static final Comparator<Order> ASK_COMPARATOR =
            Comparator.comparingDouble(Order::getPrice)
                    .thenComparingLong(Order::getTimestamp);

    public void addOrder(Order order) {
        String symbol = order.getSymbol();
        if ("BUY".equals(order.getSide())) {
            bids.computeIfAbsent(symbol, k -> new PriorityQueue<>(BID_COMPARATOR))
                    .offer(order);
        } else {
            asks.computeIfAbsent(symbol, k -> new PriorityQueue<>(ASK_COMPARATOR))
                    .offer(order);
        }
    }

    public PriorityQueue<Order> getBids(String symbol) {
        return bids.getOrDefault(symbol, new PriorityQueue<>(BID_COMPARATOR));
    }

    public PriorityQueue<Order> getAsks(String symbol) {
        return asks.getOrDefault(symbol, new PriorityQueue<>(ASK_COMPARATOR));
    }

    public void removeOrder(String symbol, String side, String orderId) {
        if ("BUY".equals(side)) {
            PriorityQueue<Order> heap = bids.get(symbol);
            if (heap != null) heap.removeIf(o -> o.getOrderId().equals(orderId));
        } else {
            PriorityQueue<Order> heap = asks.get(symbol);
            if (heap != null) heap.removeIf(o -> o.getOrderId().equals(orderId));
        }
    }
}