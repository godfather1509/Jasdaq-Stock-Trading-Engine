package com.tradingSystem.Jasdaq.companies;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Id;

import com.tradingSystem.Jasdaq.Engine.Order;
import com.tradingSystem.Jasdaq.Engine.Trade;

@Entity
@Table(name = "Companies")
@Getter
@Setter
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

    @JsonManagedReference
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Order> orders;
    
    @JsonManagedReference
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Trade> tradeIds;

    public Companies(String id, String symbol, String name, long currentPrice, int shares){
        this.companyId=id;
        this.symbol=symbol;
        this.name=name;
        this.currentPrice=currentPrice;
        this.shares=shares;
    }

    @Override
    public String toString() {
        return "Companies{" +
                "companyId='" + companyId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", currentPrice=" + currentPrice +
                ", shares=" + shares +
                '}';
    }
}
