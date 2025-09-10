package com.tradingSystem.Jasdaq.Engine;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestOrder {

    @NotBlank(message = "Please provide company symbol")
    private String symbol;

    @NotBlank(message = "Please provide buy or sell")
    private boolean buySell;

    @NotBlank(message = "Please provide limit order or market order")
    private boolean marketLimit;

    @NotBlank(message = "Please provide no. of shares")
    private int shares;

    @NotBlank(message = "Please quote price")
    private long price;
    
}
