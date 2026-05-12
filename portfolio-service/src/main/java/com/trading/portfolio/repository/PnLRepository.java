package com.trading.portfolio.repository;

import com.trading.portfolio.entity.PnLEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PnLRepository extends JpaRepository<PnLEntity, Long> {

    List<PnLEntity> findByUserIdOrderByTimestampDesc(String userId);

    List<PnLEntity> findByUserIdAndSymbol(String userId, String symbol);
}