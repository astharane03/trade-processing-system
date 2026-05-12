package com.trading.portfolio.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.events.TradeExecutedEvent;
import com.trading.portfolio.entity.EventStoreEntity;
import com.trading.portfolio.entity.ReplayCheckpointEntity;
import com.trading.portfolio.repository.EventStoreRepository;
import com.trading.portfolio.repository.ReplayCheckpointRepository;
import com.trading.portfolio.service.PortfolioService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventReplayService {

    private static final String CHECKPOINT_KEY = "TRADE_REPLAY";

    private final EventStoreRepository eventStoreRepository;
    private final ReplayCheckpointRepository checkpointRepository;
    private final PortfolioService portfolioService;
    private final ObjectMapper objectMapper;

    /*
     * HOW THIS WORKS:
     *
     * replay_checkpoint table stores the last event_store.id we processed.
     *
     * Fresh start (no checkpoint):
     *   → replay ALL TRADE_EXECUTED events from beginning
     *   → save checkpoint with last event id
     *
     * Crash + restart (checkpoint exists):
     *   → read checkpoint → last processed id = 500
     *   → only replay events with id > 500
     *   → no deleteAll(), no double counting, no full table scan
     *
     * This is production-grade — used in real event sourcing systems.
     * Interview talking point: "incremental replay with checkpoint tracking"
     */
    @PostConstruct
    @Transactional
    public void replayOnStartup() {
        log.info("Checking replay checkpoint...");

        // find where we left off
        Long lastProcessedId = checkpointRepository
                .findById(CHECKPOINT_KEY)
                .map(ReplayCheckpointEntity::getLastProcessedEventId)
                .orElse(0L);  // 0 = never replayed = start from beginning

        log.info("Last processed event_store id = {}", lastProcessedId);

        // only fetch events AFTER the checkpoint — not everything
        List<EventStoreEntity> pendingEvents = eventStoreRepository
                .findByEventTypeAndIdGreaterThanOrderByIdAsc(
                        "TRADE_EXECUTED", lastProcessedId);

        if (pendingEvents.isEmpty()) {
            log.info("No new events to replay. Portfolio state is current.");
            return;
        }

        log.info("Replaying {} new TRADE_EXECUTED events...", pendingEvents.size());

        long lastId = lastProcessedId;
        int replayed = 0;
        int failed = 0;

        for (EventStoreEntity stored : pendingEvents) {
            try {
                TradeExecutedEvent event = objectMapper.readValue(
                        stored.getPayload(), TradeExecutedEvent.class);

                portfolioService.processTrade(event);

                lastId = stored.getId(); // track progress
                replayed++;

            } catch (Exception e) {
                log.error("Failed to replay event id={} error={}",
                        stored.getId(), e.getMessage());
                failed++;
                // continue — don't let one bad event stop the whole replay
            }
        }

        // save updated checkpoint
        checkpointRepository.save(ReplayCheckpointEntity.builder()
                .checkpointKey(CHECKPOINT_KEY)
                .lastProcessedEventId(lastId)
                .updatedAt(System.currentTimeMillis())
                .build());

        log.info("Replay complete. replayed={} failed={} checkpoint={}",
                replayed, failed, lastId);
    }
}