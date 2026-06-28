package com.tradingSystem.Jasdaq.Engine;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TradeRepository extends JpaRepository<Trade, String> {

    @Query(value = "SELECT COALESCE(SUM(price * quantity), 0) FROM Trades", nativeQuery = true)
    Long getTotalVolume();

    Page<Trade> findByCompanyCompanyIdOrderByTradeTimeAsc(String companyId, Pageable pageable);

    List<Trade> findBySymbolOrderByTradeTimeDesc(String symbol);
}
