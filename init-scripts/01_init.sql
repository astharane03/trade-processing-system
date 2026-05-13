CREATE TABLE IF NOT EXISTS orders (
    order_id            VARCHAR(36)     PRIMARY KEY,
    idempotency_key     VARCHAR(36)     NOT NULL UNIQUE,
    user_id             VARCHAR(100)    NOT NULL,
    symbol              VARCHAR(20)     NOT NULL,
    side                VARCHAR(4)      NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type                VARCHAR(10)     NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    quantity            INTEGER         NOT NULL CHECK (quantity > 0),
    remaining_quantity  INTEGER         NOT NULL CHECK (remaining_quantity >= 0),
    price               DOUBLE PRECISION,
    status              VARCHAR(20)     NOT NULL
                            CHECK (status IN ('PENDING','FILLED',
                                              'PARTIALLY_FILLED','CANCELLED')),
    created_at          BIGINT          NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id
    ON orders(user_id);

CREATE INDEX IF NOT EXISTS idx_orders_symbol_status
    ON orders(symbol, status);

CREATE INDEX IF NOT EXISTS idx_orders_created_at
    ON orders(created_at);


CREATE TABLE IF NOT EXISTS event_store (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        VARCHAR(36)     NOT NULL UNIQUE,
    event_type      VARCHAR(50)     NOT NULL,
    aggregate_id    VARCHAR(36)     NOT NULL,
    payload         TEXT            NOT NULL,
    created_at      BIGINT          NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_store_type
    ON event_store(event_type);

CREATE INDEX IF NOT EXISTS idx_event_store_aggregate_id
    ON event_store(aggregate_id);

CREATE INDEX IF NOT EXISTS idx_event_store_type_id
    ON event_store(event_type, id);


CREATE TABLE IF NOT EXISTS portfolio_holdings (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         VARCHAR(100)    NOT NULL,
    symbol          VARCHAR(20)     NOT NULL,
    quantity        INTEGER         NOT NULL DEFAULT 0,
    avg_cost        DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_invested  DOUBLE PRECISION NOT NULL DEFAULT 0,
    unrealized_pnl  DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_updated    BIGINT,
    CONSTRAINT uq_holdings_user_symbol UNIQUE (user_id, symbol)
);

CREATE INDEX IF NOT EXISTS idx_holdings_user_id
    ON portfolio_holdings(user_id);


CREATE TABLE IF NOT EXISTS portfolio_pnl (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             VARCHAR(100)    NOT NULL,
    symbol              VARCHAR(20)     NOT NULL,
    trade_id            VARCHAR(36)     NOT NULL,
    realized_pnl        DOUBLE PRECISION NOT NULL DEFAULT 0,
    quantity_traded     INTEGER         NOT NULL,
    execution_price     DOUBLE PRECISION NOT NULL,
    side                VARCHAR(4)      NOT NULL CHECK (side IN ('BUY', 'SELL')),
    timestamp           BIGINT          NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pnl_user_id
    ON portfolio_pnl(user_id);

CREATE INDEX IF NOT EXISTS idx_pnl_user_symbol
    ON portfolio_pnl(user_id, symbol);

CREATE INDEX IF NOT EXISTS idx_pnl_timestamp
    ON portfolio_pnl(timestamp DESC);


CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(36)     PRIMARY KEY,
    processed_at    BIGINT          NOT NULL
);


CREATE TABLE IF NOT EXISTS replay_checkpoint (
    checkpoint_key          VARCHAR(50)     PRIMARY KEY,
    last_processed_event_id BIGINT          NOT NULL DEFAULT 0,
    updated_at              BIGINT
);

INSERT INTO replay_checkpoint (checkpoint_key, last_processed_event_id, updated_at)
VALUES ('TRADE_REPLAY', 0, 0)
ON CONFLICT (checkpoint_key) DO NOTHING;