package com.trading.order.api;

import com.trading.common.dto.OrderRequest;
import com.trading.common.dto.OrderResponse;
import com.trading.order.entity.OrderEntity;
import com.trading.order.repository.OrderRepository;
import com.trading.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    // POST — place new order
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody OrderRequest request) {

        log.info("Received order request userId={} symbol={} side={} qty={}",
                request.getUserId(), request.getSymbol(),
                request.getSide(), request.getQuantity());

        OrderResponse response = orderService.placeOrder(request);

        HttpStatus status = switch (response.getStatus()) {
            case "DUPLICATE" -> HttpStatus.OK;
            case "ACCEPTED"  -> HttpStatus.CREATED;
            case "MATCHED"   -> HttpStatus.CREATED;
            default          -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(response);
    }

    // GET — all orders for a user
    // GET http://localhost:8080/api/orders/user/alice
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderEntity>> getOrdersByUser(
            @PathVariable String userId) {

        log.info("Fetching orders for userId={}", userId);
        return ResponseEntity.ok(orderRepository.findByUserId(userId));
    }

    // GET — orders by symbol and status
    // GET http://localhost:8080/api/orders/symbol/AAPL/status/PENDING
    @GetMapping("/symbol/{symbol}/status/{status}")
    public ResponseEntity<List<OrderEntity>> getOrdersBySymbolAndStatus(
            @PathVariable String symbol,
            @PathVariable String status) {

        return ResponseEntity.ok(
                orderRepository.findBySymbolAndStatus(symbol, status));
    }

    // GET — health
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("order-service is up");
    }
}