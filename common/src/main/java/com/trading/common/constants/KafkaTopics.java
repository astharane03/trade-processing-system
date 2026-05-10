package com.trading.common.constants;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String ORDERS_PLACED       = "orders.placed";
    public static final String TRADES_EXECUTED     = "trades.executed";
    public static final String PORTFOLIO_UPDATES   = "portfolio.updates";
    public static final String RISK_ALERTS         = "risk.alerts";

    public static final String ORDERS_PLACED_DLQ   = "orders.placed.dlq";
    public static final String TRADES_EXECUTED_DLQ = "trades.executed.dlq";
}