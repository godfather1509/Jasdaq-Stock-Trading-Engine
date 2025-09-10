package com.tradingSystem.Jasdaq.Engine;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public List<Order> allOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> singleOrder(String id) {
        return orderRepository.findById(id);
    }

    public Order createOrder(Order order) {
        orderRepository.save(order);
        return order;
    }

}