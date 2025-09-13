package com.tradingSystem.Jasdaq.companies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tradingSystem.Jasdaq.Engine.EngineService;

@Service
class PlaceOrder {
    @Autowired
    private static EngineService engineService;

    public static void saveOrder(boolean buySell, long price, int shares, boolean marketLimit, String companyId){
        engineService.placeOrder(buySell, price, shares, marketLimit, companyId);
    }

}
