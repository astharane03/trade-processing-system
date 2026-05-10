package com.trading.order.api;

import com.trading.common.dto.OrderRequest;
import com.trading.common.dto.OrderResponse;
import com.trading.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {

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

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("order-service is up");
    }
}