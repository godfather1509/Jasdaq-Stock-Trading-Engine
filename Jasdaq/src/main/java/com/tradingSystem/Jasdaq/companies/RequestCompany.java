package com.tradingSystem.Jasdaq.companies;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // this reduces boilerplate code and gives statndard functions for all fields
@AllArgsConstructor
@NoArgsConstructor
public class RequestCompany {

    @NotBlank(message = "Please provide symbol")
    private String symbol;

    @NotBlank(message = "Please provide Name")
    private String name;    

    @NotBlank(message = "Please provide price")
    @Min(value = 1, message = "Price must be at least $1")
    private long currentPrice;

    @NotBlank(message = "Please provide no. of shares")
    @Min(value = 1, message = "There must be at least 1 Share")
    private int shares;
}
