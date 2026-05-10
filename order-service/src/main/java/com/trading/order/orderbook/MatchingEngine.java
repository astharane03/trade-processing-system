package com.trading.order.orderbook;

import com.trading.common.events.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingEngine {

    private final OrderBook orderBook;

    /*
     * MATCHING ALGORITHM:
     * 1. peek best BUY (highest price) and best SELL (lowest price)
     * 2. if bestBid.price >= bestAsk.price → MATCH
     * 3. execution price = ask price (seller sets the price)
     * 4. matched qty = min(bid.remaining, ask.remaining)
     * 5. reduce both remaining quantities
     * 6. if remaining = 0 → remove from heap
     * 7. repeat until no more matches
     */

    public List<TradeExecutedEvent> match(String symbol) {
        List<TradeExecutedEvent> trades = new ArrayList<>();

        PriorityQueue<Order> bidHeap = orderBook.getBids(symbol);
        PriorityQueue<Order> askHeap = orderBook.getAsks(symbol);

        while (!bidHeap.isEmpty() && !askHeap.isEmpty()) {

            Order bestBid = bidHeap.peek();
            Order bestAsk = askHeap.peek();

            // no match condition
            if (bestBid.getPrice() < bestAsk.getPrice()) {
                break;
            }

            // match found
            int matchedQty = Math.min(bestBid.getRemainingQuantity(), bestAsk.getRemainingQuantity());

            double executionPrice = bestAsk.getPrice(); // price-time: ask sets price

            log.info("MATCH: {} shares of {} at ${} | buyer={} seller={}",
                    matchedQty, symbol, executionPrice,
                    bestBid.getUserId(), bestAsk.getUserId());

            // build trade event
            TradeExecutedEvent trade = TradeExecutedEvent.builder()
                    .tradeId(UUID.randomUUID().toString())
                    .buyOrderId(bestBid.getOrderId())
                    .sellOrderId(bestAsk.getOrderId())
                    .symbol(symbol)
                    .quantity(matchedQty)
                    .executionPrice(executionPrice)
                    .buyUserId(bestBid.getUserId())
                    .sellUserId(bestAsk.getUserId())
                    .timestamp(System.currentTimeMillis())
                    .build();

            trades.add(trade);

            // update remaining quantities
            bestBid.setRemainingQuantity(bestBid.getRemainingQuantity() - matchedQty);
            bestAsk.setRemainingQuantity(bestAsk.getRemainingQuantity() - matchedQty);

            // remove fully filled orders from heap
            if (bestBid.getRemainingQuantity() == 0) {
                bidHeap.poll();
            }
            if (bestAsk.getRemainingQuantity() == 0) {
                askHeap.poll();
            }
        }

        return trades;
    }
}