package com.tradingSystem.Jasdaq.Engine;

// import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TradeRepository extends JpaRepository<Trade, String> {

    @Query(value = "SELECT COALESCE(SUM(price * quantity), 0) FROM Trades", nativeQuery = true)
    Long getTotalVolume();

    java.util.List<Trade> findByCompanyCompanyIdOrderByTradeTimeAsc(String companyId);
    
}
