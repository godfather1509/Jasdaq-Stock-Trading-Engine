package com.tradingSystem.Jasdaq.companies;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Id;

import com.tradingSystem.Jasdaq.Engine.Order;
import com.tradingSystem.Jasdaq.Engine.Trade;

@Entity
@Table(name = "Companies")
@Data // this reduces boilerplate code and gives statndard functions for all fields
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Companies {

    @Id
    private String companyId;

    @Column(nullable=false)
    private String symbol;
    @Column(nullable=false)
    private String name;    
    private long currentPrice;
    private int shares;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Order> orders;
    // orphanRemoval true means if we remove an order from orders list then it will be removed from Order talble as well
    
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Trade> tradeIds;

    public Companies(String id, String symbol, String name, long currentPrice, int shares){
        this.companyId=id;
        this.symbol=symbol;
        this.name=name;
        this.currentPrice=currentPrice;
        this.shares=shares;
    }       
}
