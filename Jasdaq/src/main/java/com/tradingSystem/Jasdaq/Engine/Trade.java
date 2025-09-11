package com.tradingSystem.Jasdaq.Engine;

import com.tradingSystem.Jasdaq.companies.Companies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "Trades")
@Data
@NoArgsConstructor
public class Trade {

    @Id
    private String tradeId;

    @Column(nullable=false)
    private String symbol;
    @Column(nullable=false)
    private String buyOrderId;
    @Column(nullable=false)
    private String sellOrderId;
    @Column(nullable=false)
    private long price;
    @Column(nullable=false)
    private int quantity;

    private long tradeTime;

    @ManyToOne
    @JoinColumn(name = "companyId")
    private Companies company;

    @PrePersist
    protected void onCreate(){
        this.tradeTime=System.currentTimeMillis();
    }

    public Trade(String tradeId, String buyOrderId, String sellOrderId, long price, int quantity, String sym) {
        this.symbol = sym;
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.tradeTime = System.currentTimeMillis();
        this.price = price;
        this.quantity = quantity;
    }
    
}
