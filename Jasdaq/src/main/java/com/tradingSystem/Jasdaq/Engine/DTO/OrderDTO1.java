package com.tradingSystem.Jasdaq.Engine.DTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderDTO1 {

    @NotBlank(message = "Please provide company symbol")
    private String symbol;

    @NotNull(message = "Please provide buy or sell")
    private Boolean buySell;

    @NotNull(message = "Please provide limit order or market order")
    private Boolean marketLimit;

    @NotNull(message = "Please provide no. of shares")
    @Min(value = 1, message = "Shares must be at least 1")
    private Integer shares;

    @NotNull(message = "Please quote price")
    @Min(value = 0, message = "Price cannot be negative")
    private Long price;

    @NotBlank(message = "Please provide company id")
    private String companyId;
}
