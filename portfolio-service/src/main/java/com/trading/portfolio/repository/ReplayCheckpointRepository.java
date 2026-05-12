package com.trading.portfolio.repository;

import com.trading.portfolio.entity.ReplayCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReplayCheckpointRepository
        extends JpaRepository<ReplayCheckpointEntity, String> {
}