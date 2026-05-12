package com.trading.portfolio.repository;

import com.trading.portfolio.entity.HoldingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<HoldingEntity, Long> {

    Optional<HoldingEntity> findByUserIdAndSymbol(String userId, String symbol);

    List<HoldingEntity> findByUserId(String userId);
}