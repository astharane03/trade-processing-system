package com.trading.notifier.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.events.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    /*
     * One session per connected user.
     * key   = userId (extracted from URL query param)
     * value = live WebSocket session
     *
     * ConcurrentHashMap — thread safe.
     * Multiple Kafka consumer threads can call sendNotification()
     * simultaneously for different users without race conditions.
     */
    private final ConcurrentHashMap<String, WebSocketSession> sessions
            = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        if (userId != null) {
            sessions.put(userId, session);
            log.info("Notification WebSocket connected userId={} sessionId={}",
                    userId, session.getId());
        } else {
            log.warn("WebSocket connected without userId — closing. sessionId={}",
                    session.getId());
            try { session.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.values().remove(session);
        log.info("Notification WebSocket disconnected sessionId={} status={}",
                session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // clients only receive — they do not send messages to this handler
        log.debug("Ignored inbound message from sessionId={}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error sessionId={} error={}",
                session.getId(), exception.getMessage());
        sessions.values().remove(session);
    }

    // ── Push notification ─────────────────────────────────────────────────────

    /*
     * Called by TradeNotificationConsumer for EACH user involved in a trade.
     * Both buyer and seller get notified separately.
     *
     * Payload sent to browser:
     * {
     *   "tradeId": "...",
     *   "symbol": "AAPL",
     *   "quantity": 100,
     *   "executionPrice": 150.00,
     *   "side": "BUY",          ← personalised per user
     *   "message": "Your BUY order for 100 AAPL at $150.00 was filled",
     *   "timestamp": 1234567890
     * }
     */
    public void sendTradeNotification(String userId,
                                      TradeExecutedEvent event,
                                      String side) {
        WebSocketSession session = sessions.get(userId);

        if (session == null || !session.isOpen()) {
            log.debug("No active WebSocket session for userId={} — notification skipped",
                    userId);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "tradeId",        event.getTradeId(),
                    "symbol",         event.getSymbol(),
                    "quantity",       event.getQuantity(),
                    "executionPrice", event.getExecutionPrice(),
                    "side",           side,
                    "message",        buildMessage(side, event),
                    "timestamp",      event.getTimestamp()
            );

            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));

            log.info("Notification pushed userId={} side={} symbol={} qty={} price={}",
                    userId, side, event.getSymbol(),
                    event.getQuantity(), event.getExecutionPrice());

        } catch (Exception e) {
            log.error("Failed to push notification to userId={} error={}",
                    userId, e.getMessage());
            // remove broken session — client must reconnect
            sessions.remove(userId);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildMessage(String side, TradeExecutedEvent event) {
        return String.format(
                "Your %s order for %d %s at $%.2f was filled",
                side,
                event.getQuantity(),
                event.getSymbol(),
                event.getExecutionPrice()
        );
    }

    private String extractUserId(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("userId=")) {
                        return param.substring("userId=".length());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract userId from WebSocket URI: {}",
                    session.getUri());
        }
        return null;
    }

    // ── Utility (used by controller for monitoring) ───────────────────────────

    public int getActiveSessionCount() {
        return (int) sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .count();
    }
}