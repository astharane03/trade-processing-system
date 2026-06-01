# Distributed Trade Processing System

An event-driven financial exchange built with Java, Spring Boot, Kafka, Redis, and PostgreSQL. Implements a heap-based order matching engine across four decoupled microservices with full fault tolerance, event sourcing, and real-time WebSocket push.

## Architecture

```
Client
  │
  ▼
Order Service :8080          ← REST API · Redis dedup · Order book · Matching engine · Event store
  │
  ├── Kafka: orders.placed
  └── Kafka: trades.executed
              │
              ├── Portfolio Service :8081   ← Holdings · P&L · Crash recovery · WebSocket push
              │         │
              │         └── Kafka: portfolio.updates
              │                       │
              │                       └── Risk Service :8082   ← VaR · Position limits (stateless)
              │
              └── Notifier Service :8083   ← Real-time WebSocket push to buyer + seller (stateless)
```

## Tech Stack

`Java 17` · `Spring Boot 3.5` · `Apache Kafka (Redpanda)` · `PostgreSQL 16` · `Redis 7` · `Docker`

## Key Engineering Decisions

**Heap-based order book** — max-heap for bids (highest price at top), min-heap for asks (lowest price at top), `ConcurrentHashMap` per symbol. O(1) best-price lookup, O(log n) insertion. Price-time priority via timestamp tiebreaker in the comparator.

**Kafka publish outside `@Transactional`** — publishing inside a transaction means a DB rollback after Kafka publish creates a ghost event downstream. `publishEvents()` runs after `saveOrderAndMatch()` returns and the DB has committed.

**Incremental checkpoint replay** — on crash recovery, only events with `id > lastCheckpoint` are replayed from the event store. No `deleteAll()`, no full-table scan, no double-counting.

**Two-layer idempotency** — Redis `SETNX` with 24h TTL at the REST layer (duplicate HTTP requests) and Kafka producer idempotence config at the broker layer (duplicate message on producer retry). A `processed_events` table handles Kafka at-least-once redelivery on the consumer side.

**Dead Letter Queue** — `DefaultErrorHandler` retries failed Kafka events 3× with 1000ms backoff. `DeadLetterPublishingRecoverer` sends to `{topic}.dlq` on exhaustion. Events are never silently lost.

**Stateless risk + notifier services** — all data needed is in the Kafka event itself. No DB, no shared state, trivially horizontally scalable.

## Running Locally

Prerequisites: Docker, Java 17, Maven 3.9

```bash
git clone https://github.com/yourusername/trade-processing-system
cd trade-processing-system
docker-compose up -d --build
```

Starts 9 containers — PostgreSQL, Redis, Redpanda (Kafka), Redpanda Console, topic initializer, and all 4 services. Wait ~30s then verify:

```bash
curl http://localhost:8080/api/orders/health
curl http://localhost:8081/api/portfolio/health
curl http://localhost:8082/api/risk/health
curl http://localhost:8083/api/notifications/health
```

Kafka UI → http://localhost:8090

## Quick Test

```bash
# Place a BUY order (no match yet)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"k1","userId":"alice","symbol":"AAPL","side":"BUY","type":"LIMIT","quantity":100,"price":150.00}'
# status: ACCEPTED

# Place matching SELL — triggers trade execution
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"k2","userId":"bob","symbol":"AAPL","side":"SELL","type":"LIMIT","quantity":100,"price":148.00}'
# status: MATCHED

# Verify holdings updated
curl http://localhost:8081/api/portfolio/alice/holdings

# Retry same request — idempotency check
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"k1","userId":"alice","symbol":"AAPL","side":"BUY","type":"LIMIT","quantity":100,"price":150.00}'
# status: DUPLICATE
```

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/orders` | Place an order |
| `GET` | `/api/orders/user/{userId}` | Orders by user |
| `GET` | `/api/portfolio/{userId}/holdings` | Current holdings |
| `GET` | `/api/portfolio/{userId}/pnl` | P&L history |
| `WS` | `ws://localhost:8081/ws/portfolio?userId=X` | Live portfolio updates |
| `WS` | `ws://localhost:8083/ws/notifications?userId=X` | Trade execution alerts |

## Tests

```bash
mvn test -pl common,order-service,portfolio-service
```

Covers `OrderBook` (heap ordering, price-time priority), `MatchingEngine` (no-match, full match, partial fill, multiple matches), and `PnLCalculator` (weighted avg cost, realized/unrealized P&L).

## Kafka Topics

| Topic | Partitions | Flow |
|-------|-----------|------|
| `orders.placed` | 3 | order-service → audit |
| `trades.executed` | 3 | order-service → portfolio-service, notifier-service |
| `portfolio.updates` | 3 | portfolio-service → risk-service |
| `risk.alerts` | 3 | risk-service → future consumers |
| `trades.executed.dlq` | 1 | failed events after 3 retries |
| `portfolio.updates.dlq` | 1 | failed events after 3 retries |

`portfolio-service` and `notifier-service` use different consumer groups on `trades.executed` — Kafka delivers each trade to both independently (fan-out).
