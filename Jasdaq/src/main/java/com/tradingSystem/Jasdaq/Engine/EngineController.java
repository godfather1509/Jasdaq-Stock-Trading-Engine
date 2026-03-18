package com.tradingSystem.Jasdaq.Engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.tradingSystem.Jasdaq.Engine.DTO.OrderDTO1;
import com.tradingSystem.Jasdaq.Engine.DTO.OrderDTO2;

@Controller
public class EngineController {

    @Autowired
    private EngineService engineService;

    // Frontend sends to /app/placeOrder
    @MessageMapping("/placeOrder")
    public void sendOrder(@Payload OrderDTO1 request) {
        engineService.placeOrder(request.isBuySell(), request.getPrice(), request.getShares(),
                request.isMarketLimit(), request.getCompanyId());
    }

    // Frontend sends to /app/cancelOrder
    @MessageMapping("/cancelOrder")
    public void cancelOrder(@Payload OrderDTO2 request) {
        engineService.cancelOrder(request.getOrderId(), request.getCompanyId());
    }
}
