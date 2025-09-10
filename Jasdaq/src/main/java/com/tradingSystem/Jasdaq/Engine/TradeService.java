package com.tradingSystem.Jasdaq.Engine;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;

    public List<Trade> allTrades(){
        return tradeRepository.findAll();
    }

    public Optional<Trade> singleTrade(String id){
        return tradeRepository.findById(id);
    }

    public Trade createTrade(Trade trade){
        tradeRepository.save(trade);
        return trade;
    }
    
}
