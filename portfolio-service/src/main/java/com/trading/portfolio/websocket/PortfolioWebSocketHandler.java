package com.trading.portfolio.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.events.PortfolioUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioWebSocketHandler extends TextWebSocketHandler {

    // userId → active WebSocket session
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // extract userId from WebSocket URL query param
        // ws://localhost:8081/ws/portfolio?userId=user-1
        String userId = extractUserId(session);
        if (userId != null) {
            sessions.put(userId, session);
            log.info("WebSocket connected userId={} sessionId={}",
                    userId, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.values().remove(session);
        log.info("WebSocket disconnected sessionId={}", session.getId());
    }

    public void sendPortfolioUpdate(String userId, PortfolioUpdatedEvent event) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                session.sendMessage(new TextMessage(payload));
                log.info("WebSocket pushed portfolio update userId={}", userId);
            } catch (Exception e) {
                log.error("WebSocket push failed userId={} error={}",
                        userId, e.getMessage());
            }
        } else {
            log.debug("No active WebSocket session for userId={}", userId);
        }
    }

    private String extractUserId(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery(); // "userId=user-1"
            if (query != null && query.startsWith("userId=")) {
                return query.split("=")[1];
            }
        } catch (Exception e) {
            log.warn("Could not extract userId from WebSocket URI");
        }
        return null;
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session, TextMessage message) {
        // clients don't send messages — server pushes only
        log.debug("Received message from client (ignored): {}", message.getPayload());
    }
}