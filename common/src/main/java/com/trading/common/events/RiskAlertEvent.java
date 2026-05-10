package com.trading.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAlertEvent {

    private String alertId;
    private String userId;
    private String alertType;      // "POSITION_LIMIT_BREACH" or "VAR_BREACH"
    private String symbol;
    private Double currentValue;   // current position size or VaR value
    private Double limitValue;     // the threshold that was breached
    private String message;
    private long timestamp;
}