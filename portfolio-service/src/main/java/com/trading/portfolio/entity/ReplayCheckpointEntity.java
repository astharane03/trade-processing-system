package com.trading.portfolio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "replay_checkpoint")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayCheckpointEntity {

    @Id
    @Column(name = "checkpoint_key")
    private String checkpointKey;      // fixed value: "TRADE_REPLAY"

    @Column(name = "last_processed_event_id")
    private Long lastProcessedEventId; // event_store.id of last replayed row

    @Column(name = "updated_at")
    private Long updatedAt;
}