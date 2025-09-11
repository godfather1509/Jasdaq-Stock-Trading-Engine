package com.tradingSystem.Jasdaq.Engine.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderDTO2 {

    @NotBlank(message = "Please provide order id")
    private String orderId; 

    @NotBlank(message = "Please provide company id")
    private String companyId;    
    
}
