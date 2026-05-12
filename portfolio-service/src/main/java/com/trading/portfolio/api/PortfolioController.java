package com.trading.portfolio.api;

import com.trading.portfolio.entity.HoldingEntity;
import com.trading.portfolio.entity.PnLEntity;
import com.trading.portfolio.repository.HoldingRepository;
import com.trading.portfolio.repository.PnLRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final HoldingRepository holdingRepository;
    private final PnLRepository pnLRepository;

    /*
     * GET all holdings for a user.
     * Shows every stock they currently own with avgCost + unrealizedPnL.
     * URL: GET http://localhost:8081/api/portfolio/alice/holdings
     */
    @GetMapping("/{userId}/holdings")
    public ResponseEntity<List<HoldingEntity>> getHoldings(
            @PathVariable String userId) {

        log.info("Fetching holdings for userId={}", userId);
        List<HoldingEntity> holdings = holdingRepository.findByUserId(userId);

        if (holdings.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204 — user has no holdings
        }
        return ResponseEntity.ok(holdings);
    }

    /*
     * GET full P&L history for a user — newest first.
     * Shows every trade they made and what profit/loss it generated.
     * URL: GET http://localhost:8081/api/portfolio/alice/pnl
     */
    @GetMapping("/{userId}/pnl")
    public ResponseEntity<List<PnLEntity>> getPnLHistory(
            @PathVariable String userId) {

        log.info("Fetching P&L history for userId={}", userId);
        List<PnLEntity> history =
                pnLRepository.findByUserIdOrderByTimestampDesc(userId);

        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history);
    }

    /*
     * GET P&L for a specific symbol only.
     * URL: GET http://localhost:8081/api/portfolio/alice/pnl/AAPL
     */
    @GetMapping("/{userId}/pnl/{symbol}")
    public ResponseEntity<List<PnLEntity>> getPnLBySymbol(
            @PathVariable String userId,
            @PathVariable String symbol) {

        log.info("Fetching P&L for userId={} symbol={}", userId, symbol);
        List<PnLEntity> history =
                pnLRepository.findByUserIdAndSymbol(userId, symbol);

        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("portfolio-service is up");
    }
}