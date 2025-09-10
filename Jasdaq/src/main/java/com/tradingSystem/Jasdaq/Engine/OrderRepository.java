package com.tradingSystem.Jasdaq.Engine;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {
    
}
