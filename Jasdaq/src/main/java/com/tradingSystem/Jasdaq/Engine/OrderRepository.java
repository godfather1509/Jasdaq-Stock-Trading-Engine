package com.tradingSystem.Jasdaq.Engine;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCompanyCompanyId(String companyId);

    List<Order> findBySymbolOrderByEntryTimeDesc(String symbol);

    // Oldest-first: used to rebuild the order book on startup so price-time (FIFO)
    // priority is preserved (placeOrder appends to the tail of each price level).
    List<Order> findBySymbolOrderByEntryTimeAsc(String symbol);

}
