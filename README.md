# Distributed Trade Processing System

A production-quality financial exchange simulator built with Java, Spring Boot, Kafka, Redis, and PostgreSQL. Implements a heap-based order matching engine across four decoupled microservices with full fault tolerance, event sourcing, and real-time WebSocket push.

> Built as a portfolio project targeting SDE-2 roles at financial technology companies. Every design decision — from the order book algorithm to the crash recovery strategy — mirrors patterns used in production trading systems.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Client / Browser                            │
│              POST /api/orders          WebSocket push               │
└──────────────────────┬───────────────────────┬─────────────────────┘
                       │                       │
              ┌────────▼────────┐              │
              │  Order Service  │              │
              │    :8080        │              │
              │                 │              │
              │ • Redis dedup   │              │
              │ • Heap order    │              │
              │   book          │              │
              │ • Matching      │              │
              │   engine        │              │
              │ • Event store   │              │
              └────────┬────────┘              │
                       │                       │
              ┌────────▼────────────────────┐  │
              │       Kafka Event Bus        │  │
              │  orders.placed              │  │
              │  trades.executed ───────────┼──┼──────────────────┐
              │  portfolio.updates          │  │                  │
              │  risk.alerts               │  │                  │
              └─────┬───────────┬──────────┘  │                  │
                    │           │              │                  │
           ┌────────▼──┐  ┌────▼──────────┐   │         ┌───────▼──────────┐
           │ Portfolio │  │ Risk Service  │   │         │ Notifier Service  │
           │ Service   │  │   :8082       │   │         │     :8083         │
           │  :8081    │  │               │   │         │                   │
           │           │  │ • VaR check   │   │         │ • WebSocket push  │
           │ • P&L     │  │ • Position    │   │         │ • Buyer + seller  │
           │ • Holdings│  │   limits      │   │         │   notified        │
           │ • WS push │  │ • Stateless   │   │         │ • Stateless       │
           │ • Replay  │  └───────────────┘   │         └───────────────────┘
           └─────┬─────┘                      │
                 │                            │
         ┌───────▼────────┐         ┌─────────▼──────────┐
         │   PostgreSQL   │         │       Redis         │
         │  6 tables      │         │  Idempotency keys   │
         │  event_store   │         │  SETNX + 24h TTL    │
         └────────────────┘         └────────────────────-┘
```

---

## Services

| Service | Port | Responsibility | Database |
|---|---|---|---|
| **order-service** | 8080 | REST API, order book, matching engine, event sourcing | PostgreSQL, Redis |
| **portfolio-service** | 8081 | Holdings tracking, P&L computation, crash recovery | PostgreSQL |
| **risk-service** | 8082 | VaR calculation, position limit checks | None (stateless) |
| **notifier-service** | 8083 | Real-time WebSocket trade notifications | None (stateless) |

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Core language |
| Spring Boot | 3.5 | REST, Kafka, JPA, WebSocket, DI |
| Apache Kafka (Redpanda) | 23.3 | Async event bus — 6 topics |
| Redis | 7.2 | Idempotency key storage |
| PostgreSQL | 16 | Persistent storage — 6 tables |
| Docker + docker-compose | — | One-command local deployment |
| JUnit 5 + Mockito | — | Unit tests for core algorithms |
| Maven | 3.9 | Multi-module build |

---

## Key Engineering Decisions

### 1. Heap-based order book with price-time priority

The order book uses two `PriorityQueue`s per symbol — a max-heap for bids (highest price first) and a min-heap for asks (lowest price first). Both are stored in a `ConcurrentHashMap` keyed by symbol, so AAPL and GOOGL order books have zero contention.

- **O(1)** best-price lookup via `peek()`
- **O(log n)** insertion and removal via `offer()` / `poll()`
- **Price-time priority**: same price → earlier timestamp wins (comparator tiebreaker)
- **Partial fills**: `remainingQuantity` decreases; order stays in heap until fully filled

```
bestBid.price ≥ bestAsk.price → MATCH
matchedQty = min(bid.remaining, ask.remaining)
executionPrice = ask.price (seller sets price)
```

### 2. Kafka publish OUTSIDE the DB transaction

A common mistake: publishing to Kafka inside a `@Transactional` method. If the DB rolls back after Kafka publishes, downstream services receive events for orders that don't exist — a ghost event that's impossible to recover from.

**Fix**: `placeOrder()` is not `@Transactional`. It calls `saveOrderAndMatch()` (which is `@Transactional`) and only publishes to Kafka after that method returns and the DB has committed. The `OrderResult` object carries the event data across the transaction boundary.

```
saveOrderAndMatch() @Transactional → DB commits → publishEvents() → Kafka
```

### 3. Incremental checkpoint-based crash recovery

Naive crash recovery would `DELETE FROM portfolio_holdings` and replay all events from scratch — this takes a write lock on the entire table at scale.

**This system uses incremental replay**: a `replay_checkpoint` table stores the last processed `event_store.id`. On restart, only events with `id > lastCheckpoint` are replayed. No table lock on existing data, no double-counting.

```sql
-- Only processes missed events, not the full history
SELECT * FROM event_store
WHERE event_type = 'TRADE_EXECUTED'
AND id > lastProcessedId
ORDER BY id ASC
```

### 4. Two-layer idempotency

**Layer 1 — Redis SETNX (REST layer)**: Covers duplicate HTTP requests from network retries. Atomic `setIfAbsent(key, "PROCESSING", 24h)` — returns false = duplicate, return immediately without touching DB or Kafka.

**Layer 2 — Kafka producer idempotence**: `ENABLE_IDEMPOTENCE_CONFIG=true` with `ACKS=all`. The broker assigns sequence numbers per producer-partition and discards duplicates at the broker level.

**Layer 3 — Idempotent consumer**: `processed_events` table stores every processed `tradeId`. `existsById(tradeId)` before processing. The INSERT is the last write in `@Transactional` — if processing rolls back, the tradeId is not marked, so Kafka redelivers correctly.

### 5. Dead Letter Queue with bounded retry

Failed Kafka events are retried 3 times with a 1000ms backoff. After the 3rd failure, `DeadLetterPublishingRecoverer` sends the event to `{topic}.dlq`. The original offset is committed — the consumer moves forward. Events are never silently lost.

```
consume() throws → retry 1 (1000ms) → retry 2 (1000ms) → retry 3 (1000ms)
                → DeadLetterPublishingRecoverer → trades.executed.dlq
```

### 6. Stateless risk service

The `PortfolioUpdatedEvent` carries all data needed for risk evaluation (userId, symbol, newQuantity, averageCost). Risk service holds no state and queries no database. This means it scales horizontally with zero coordination — run 10 instances, each processes independently.

---

## Getting Started

### Prerequisites

- Docker + Docker Compose
- Java 17+
- Maven 3.9+

### Run everything with one command

```bash
git clone https://github.com/yourusername/trade-processing-system
cd trade-processing-system

# Build all service images and start everything
docker-compose up -d --build
```

This starts 9 containers: PostgreSQL, Redis, Redpanda (Kafka), Redpanda Console, topic initializer, and all 4 services.

Wait ~30 seconds for services to be healthy, then verify:

```bash
curl http://localhost:8080/api/orders/health
curl http://localhost:8081/api/portfolio/health
curl http://localhost:8082/api/risk/health
curl http://localhost:8083/api/notifications/health
```

### Kafka UI

Open [http://localhost:8090](http://localhost:8090) to inspect topics, messages, and consumer group offsets.

### Useful commands

```bash
# Stop everything (keep data)
docker-compose down

# Stop and wipe all data (fresh start)
docker-compose down -v

# Tail logs for a service
docker-compose logs -f order-service

# Check all container statuses
docker-compose ps

# List Kafka topics
docker exec trading-redpanda rpk topic list

# Open PostgreSQL shell
docker exec -it trading-postgres psql -U trader -d tradingdb

# Reset consumer offset (reprocess from beginning)
docker exec trading-redpanda rpk group seek portfolio-service-group \
  --topic trades.executed --to start
```

---

## API Reference

### Place an order

```
POST /api/orders
Content-Type: application/json
```

```json
{
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "alice",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": 100,
  "price": 150.00
}
```

**Responses:**

| Status | Body `status` | Meaning |
|---|---|---|
| `201 Created` | `ACCEPTED` | Order placed in book, no match yet |
| `201 Created` | `MATCHED` | Order matched — trades executed |
| `200 OK` | `DUPLICATE` | Same idempotency key seen before |
| `400 Bad Request` | `VALIDATION_ERROR` | Invalid fields with field-level errors |

**Example — ACCEPTED:**
```json
{
  "orderId": "f57175e6-9c26-4fa9-bdbb-4a6493c6c0e3",
  "status": "ACCEPTED",
  "message": "Order placed in book",
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "price": 150.0,
  "userId": "alice",
  "timestamp": 1747165200000
}
```

**Example — MATCHED:**
```json
{
  "orderId": "7443c0fc-4760-4290-8676-8d238f4f252f",
  "status": "MATCHED",
  "message": "Order matched — 1 trade(s) executed",
  "symbol": "AAPL",
  "side": "SELL",
  "quantity": 100,
  "price": 148.0,
  "userId": "bob",
  "timestamp": 1747165260000
}
```

### Get orders by user

```
GET /api/orders/user/{userId}
```

### Get orders by symbol and status

```
GET /api/orders/symbol/{symbol}/status/{status}
```

### Get portfolio holdings

```
GET /api/portfolio/{userId}/holdings
```

```json
[
  {
    "userId": "alice",
    "symbol": "AAPL",
    "quantity": 100,
    "avgCost": 148.0,
    "totalInvested": 14800.0,
    "unrealizedPnl": 200.0
  }
]
```

### Get P&L history

```
GET /api/portfolio/{userId}/pnl
GET /api/portfolio/{userId}/pnl/{symbol}
```

### WebSocket — live trade notifications

```
ws://localhost:8083/ws/notifications?userId={userId}
```

Push payload on every trade execution:

```json
{
  "tradeId": "a8fe8fa4-3876-457f-835b-99ffdf717c7c",
  "symbol": "AAPL",
  "quantity": 100,
  "executionPrice": 148.0,
  "side": "BUY",
  "message": "Your BUY order for 100 AAPL at $148.00 was filled",
  "timestamp": 1747165260000
}
```

### WebSocket — live portfolio updates

```
ws://localhost:8081/ws/portfolio?userId={userId}
```

---

## End-to-End Test

```bash
# 1. Place a BUY order (no match yet — sits in book)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-buy-001",
    "userId": "alice",
    "symbol": "AAPL",
    "side": "BUY",
    "type": "LIMIT",
    "quantity": 100,
    "price": 150.00
  }'
# Expected: status=ACCEPTED

# 2. Place matching SELL (triggers trade execution)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-sell-001",
    "userId": "bob",
    "symbol": "AAPL",
    "side": "SELL",
    "type": "LIMIT",
    "quantity": 100,
    "price": 148.00
  }'
# Expected: status=MATCHED

# 3. Verify holdings updated
curl http://localhost:8081/api/portfolio/alice/holdings
# Expected: alice holds 100 AAPL at $148 avg cost

# 4. Test idempotency — send same order again
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-buy-001",
    ...
  }'
# Expected: status=DUPLICATE

# 5. Test validation
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"x","userId":"u","symbol":"A","side":"INVALID","type":"LIMIT","quantity":-5,"price":100}'
# Expected: 400 VALIDATION_ERROR with field errors
```

---

## Running Tests

```bash
# Run all unit tests
mvn test -pl common,order-service,portfolio-service

# Run tests for a specific module
mvn test -pl order-service
```

**Test coverage:**

| Test class | What it tests |
|---|---|
| `OrderBookTest` | Heap ordering, price-time priority, symbol isolation, removeOrder |
| `MatchingEngineTest` | No-match, full match, partial fill, multiple matches, correct user IDs |
| `PnLCalculatorTest` | Weighted avg cost, realized P&L, unrealized P&L, full scenario |

---

## Project Structure

```
trade-processing-system/
│
├── common/                          # Shared library — no Spring Boot main class
│   └── src/main/java/com/trading/common/
│       ├── constants/KafkaTopics.java       # All topic names as constants
│       ├── dto/OrderRequest.java            # Validated request DTO (@Valid)
│       ├── dto/OrderResponse.java
│       └── events/                          # Kafka event POJOs
│           ├── OrderPlacedEvent.java
│           ├── TradeExecutedEvent.java
│           ├── PortfolioUpdatedEvent.java
│           └── RiskAlertEvent.java
│
├── order-service/                   # Port 8080
│   └── src/main/java/com/trading/order/
│       ├── api/OrderController.java
│       ├── api/GlobalExceptionHandler.java
│       ├── config/KafkaConfig.java          # Idempotent producer, acks=all
│       ├── config/RedisConfig.java
│       ├── entity/OrderEntity.java          # maps to `orders` table
│       ├── entity/EventStoreEntity.java     # maps to `event_store` table
│       ├── kafka/OrderEventPublisher.java
│       ├── orderbook/Order.java             # In-memory order object
│       ├── orderbook/OrderBook.java         # ConcurrentHashMap of heaps
│       ├── orderbook/MatchingEngine.java    # Price-time priority matching
│       ├── repository/OrderRepository.java
│       ├── repository/EventStoreRepository.java
│       └── service/
│           ├── IdempotencyService.java      # Redis SETNX
│           ├── OrderBookReplayService.java  # @PostConstruct heap rebuild
│           ├── OrderResult.java             # Carries data across @Tx boundary
│           └── OrderService.java           # Orchestrates full flow
│
├── portfolio-service/               # Port 8081
│   └── src/main/java/com/trading/portfolio/
│       ├── api/PortfolioController.java
│       ├── api/GlobalExceptionHandler.java
│       ├── config/KafkaConfig.java          # Manual ack, DLQ wiring
│       ├── config/WebSocketConfig.java
│       ├── entity/                          # HoldingEntity, PnLEntity,
│       │                                    # ProcessedEventEntity,
│       │                                    # ReplayCheckpointEntity
│       ├── kafka/TradeEventConsumer.java    # Manual ack, idempotency guard
│       ├── kafka/PortfolioEventPublisher.java
│       ├── replay/EventReplayService.java   # @PostConstruct incremental replay
│       ├── repository/                      # 5 JPA repositories
│       ├── service/PnLCalculator.java       # Realized + unrealized P&L
│       ├── service/PortfolioService.java    # Buyer + seller side processing
│       └── websocket/PortfolioWebSocketHandler.java
│
├── risk-service/                    # Port 8082 — stateless
│   └── src/main/java/com/trading/risk/
│       ├── api/RiskController.java
│       ├── config/KafkaConfig.java
│       ├── kafka/PortfolioUpdateConsumer.java
│       ├── kafka/RiskAlertPublisher.java
│       └── service/
│           ├── VaRCalculator.java           # 100-scenario historical simulation
│           └── RiskEvaluationService.java   # Position limit + VaR breach
│
├── notifier-service/                # Port 8083 — stateless
│   └── src/main/java/com/trading/notifier/
│       ├── api/NotifierController.java
│       ├── config/KafkaConfig.java          # DLQ-only producer
│       ├── config/WebSocketConfig.java
│       ├── kafka/TradeNotificationConsumer.java
│       └── websocket/NotificationWebSocketHandler.java
│
├── init-scripts/
│   └── 01_init.sql                  # Creates all 6 tables with explicit indexes
│
├── docker-compose.yml               # 9 containers — infra + all 4 services
└── pom.xml                          # Parent POM — manages all 5 modules
```

---

## Database Schema

| Table | Type | Owner | Purpose |
|---|---|---|---|
| `orders` | Mutable | order-service | Current order state |
| `event_store` | Append-only | order-service writes, portfolio reads | Immutable event history — source of truth |
| `portfolio_holdings` | Mutable (UPSERT) | portfolio-service | Current shares + avg cost per user per symbol |
| `portfolio_pnl` | Append-only | portfolio-service | P&L history per trade |
| `processed_events` | Append-only | portfolio-service | Idempotent consumer guard |
| `replay_checkpoint` | Single-row | portfolio-service | Last replayed event_store ID |

Key indexes:
- `event_store(event_type, id)` — composite, for checkpoint query
- `portfolio_holdings(user_id)` — for holdings lookup
- `orders(user_id)`, `orders(symbol, status)` — for order queries

---

## Kafka Topics

| Topic | Partitions | Producer | Consumer(s) |
|---|---|---|---|
| `orders.placed` | 3 | order-service | — (audit trail) |
| `trades.executed` | 3 | order-service | portfolio-service, notifier-service |
| `portfolio.updates` | 3 | portfolio-service | risk-service |
| `risk.alerts` | 3 | risk-service | — (future consumers) |
| `trades.executed.dlq` | 1 | error handler | — |
| `portfolio.updates.dlq` | 1 | error handler | — |

`portfolio-service` and `notifier-service` use **different consumer groups** on `trades.executed` — Kafka delivers each trade to both independently, in parallel. This is the fan-out pattern.

---

## Design Tradeoffs

### Order book in-memory vs persistent

The order book is intentionally in-memory. Persisting heap state on every insert would add ~5ms per order. On restart, the book is rebuilt from the event store (two-pass replay). This is the same pattern used by most exchange matching engines — the journal is persistent, the working state is ephemeral.

### Monorepo multi-module vs separate repos

A Maven multi-module monorepo means all services share the `common` module (event POJOs, DTOs, Kafka topic constants) without duplication. A reviewer can see the entire system in one repository. Each service is still independently deployable via its own Dockerfile.

### Matching engine co-located with order-service

The matching engine and order book must communicate with zero network latency — a separate service would add a round-trip per match. At high throughput that compounds to meaningful latency. Co-location sacrifices deployment independence for performance, which is the correct tradeoff for a matching engine.

### Stateless risk and notifier services

Both consume Kafka events that carry all necessary data. No DB lookups, no shared state. This makes them trivially horizontally scalable — add more instances, point them at the same Kafka topic, done.

---

## P&L Formulas

```
New average cost (on BUY):
  newAvgCost = (existingQty × oldAvgCost + newQty × price) / (existingQty + newQty)

Realized P&L (on SELL):
  realizedPnL = (sellPrice − avgCost) × quantitySold

Unrealized P&L (paper profit/loss):
  unrealizedPnL = (currentMarketPrice − avgCost) × quantityHeld
```

---

## VaR Calculation

Historical simulation Value at Risk at 95% confidence:

1. Simulate 100 daily return scenarios: `positionValue × 2% daily volatility × sin(i × π/10)`
2. Sort ascending (worst losses first)
3. Take the 5th percentile (index 5 of 100) — maximum expected loss on 95% of trading days

```java
positionValue = newQuantity × averageCost
VaR = varCalculator.calculate(positionValue)
if (VaR > threshold) → publish RiskAlertEvent to risk.alerts
```

Non-parametric approach — makes no assumption about normal distribution, unlike delta-normal VaR.

---

## Potential Improvements

- **Cancel order endpoint** — `OrderBook.removeOrder()` exists but no REST endpoint exposes it yet
- **Market order handling** — type=MARKET accepted but treated as LIMIT; proper market order should match at any price
- **Price-level map** — replace binary heap with `TreeMap<Price, Deque<Order>>` for O(1) cancel and cleaner partial-fill semantics
- **Fixed-point price arithmetic** — replace `Double` with `long` (price in cents) to eliminate floating-point non-determinism
- **Replay optimization** — replay can re-trigger WebSocket/Kafka pushes; add a `isReplay` flag to suppress side effects during startup
- **Risk alert consumers** — `risk.alerts` topic is produced but has no consumer; a dashboard or circuit-breaker service would consume it
- **Authentication** — no auth on any endpoint; JWT or API key middleware would be the next step

---

## License

MIT
