package com.trading.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String PREFIX = "idempotency:";
    private static final long TTL_HOURS = 24;

    /*
     * SETNX = SET if Not eXists — atomic operation
     * Returns true  → key did not exist → first time seeing this request → proceed
     * Returns false → key exists       → duplicate request → reject
     */
    public boolean isFirstRequest(String idempotencyKey) {
        String redisKey = PREFIX + idempotencyKey;
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", TTL_HOURS, TimeUnit.HOURS);
        boolean isFirst = Boolean.TRUE.equals(set);
        if (!isFirst) {
            log.warn("Duplicate request detected for idempotencyKey={}", idempotencyKey);
        }
        return isFirst;
    }

    public void markCompleted(String idempotencyKey, String orderId) {
        String redisKey = PREFIX + idempotencyKey;
        // update value from PROCESSING to orderId — useful for debugging
        redisTemplate.opsForValue()
                .set(redisKey, orderId, TTL_HOURS, TimeUnit.HOURS);
        log.info("Marked idempotencyKey={} as completed with orderId={}",
                idempotencyKey, orderId);
    }

    public String getOrderId(String idempotencyKey) {
        return redisTemplate.opsForValue().get(PREFIX + idempotencyKey);
    }
}