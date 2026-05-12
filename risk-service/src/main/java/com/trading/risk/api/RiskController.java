package com.trading.risk.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("risk-service is up");
    }
}