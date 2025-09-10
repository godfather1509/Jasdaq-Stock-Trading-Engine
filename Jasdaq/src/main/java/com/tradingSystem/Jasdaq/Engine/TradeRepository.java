package com.tradingSystem.Jasdaq.Engine;

// import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, String> {

    // Optional<Trade> findTradeBy
    
}
