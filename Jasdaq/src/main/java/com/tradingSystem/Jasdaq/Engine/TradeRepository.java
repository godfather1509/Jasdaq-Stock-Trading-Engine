package com.tradingSystem.Jasdaq.Engine;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, String> {

    @Query(value = "SELECT COALESCE(SUM(price * quantity), 0) FROM Trades", nativeQuery = true)
    Long getTotalVolume();

    Page<Trade> findByCompanyCompanyIdOrderByTradeTimeAsc(String companyId, Pageable pageable);

    List<Trade> findBySymbolOrderByTradeTimeDesc(String symbol);

    @Query(value = "SELECT COALESCE(SUM(quantity), 0) FROM Trades WHERE symbol = :symbol", nativeQuery = true)
    Long getTotalQuantityBySymbol(@Param("symbol") String symbol);

    @Query(value = "SELECT COALESCE(SUM(price * quantity), 0) FROM Trades WHERE symbol = :symbol", nativeQuery = true)
    Long getTotalValueBySymbol(@Param("symbol") String symbol);

    @Query(value = "SELECT COUNT(*) FROM Trades WHERE symbol = :symbol", nativeQuery = true)
    Long getTradeCountBySymbol(@Param("symbol") String symbol);
}
