package com.tradingSystem.Jasdaq.Engine;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCompanyCompanyId(String companyId);

    @Query("SELECT AVG(o.eventTime - o.entryTime) FROM Order o WHERE o.status = true AND o.eventTime > 0")
    Double getAverageLatency();
    
}
