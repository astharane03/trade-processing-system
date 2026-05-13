package com.trading.notifier.api;

import com.trading.notifier.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotifierController {

    private final NotificationWebSocketHandler webSocketHandler;

    /*
     * Health check — also shows active WebSocket connections.
     * Useful for monitoring how many clients are connected.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "notifier-service is up",
                "activeConnections", webSocketHandler.getActiveSessionCount()
        ));
    }
}