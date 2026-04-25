package com.tradingSystem.Jasdaq.Engine;

import com.tradingSystem.Jasdaq.companies.Companies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Trades")
@Getter
@Setter
@NoArgsConstructor
public class Trade {

    @Id
    private String tradeId;

    @Column(name = "symbol", nullable = false)
    private String symbol;
    
    @Column(name = "buy_order_id", nullable = false)
    private String buyOrderId;
    
    @Column(name = "sell_order_id", nullable = false)
    private String sellOrderId;
    
    @Column(name = "price", nullable = false)
    private long price;
    
    @Column(name = "quantity", nullable = false)
    private int quantity;

    private long tradeTime;

    @ManyToOne
    @JoinColumn(name = "companyId")
    @JsonBackReference
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

    @Override
    public String toString() {
        return "Trade{" +
                "tradeId='" + tradeId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", buyOrderId='" + buyOrderId + '\'' +
                ", sellOrderId='" + sellOrderId + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", tradeTime=" + tradeTime +
                '}';
    }
}
