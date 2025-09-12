package com.tradingSystem.Jasdaq.Engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.tradingSystem.Jasdaq.Engine.DTO.OrderDTO1;
import com.tradingSystem.Jasdaq.Engine.DTO.OrderDTO2;

@Controller
public class EngineController {

    @Autowired
    private EngineService engineService;

    @MessageMapping("/placeOrder") // user will send order data at this endpoint
    @SendToUser("/queue/orders") // response will be sent to user at this endpoint
    public void sendOrder(@Payload OrderDTO1 request){
        engineService.placeOrder(request.isBuySell(), request.getPrice(), request.getShares(), request.isMarketLimit(), request.getCompanyId());
    }

    @MessageMapping("/cancelOrder")
    @SendToUser("/queue/cancel")
    public void cancelOrder(@Payload OrderDTO2 request){

        engineService.cancelOrder(request.getOrderId(), request.getCompanyId());
    }

    
}
