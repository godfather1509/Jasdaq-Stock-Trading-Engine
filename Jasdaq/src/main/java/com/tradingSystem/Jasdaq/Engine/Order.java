package com.tradingSystem.Jasdaq.Engine;

import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Orders")
@Data
@NoArgsConstructor
public class Order {

    @Id
    public String orderId;

    @Column(nullable = false)
    public boolean buySell;
    @Column(nullable = false)
    public boolean marketLimit;
    @Column(nullable = false)
    public boolean status;
    @Column(nullable = false)
    public int shares;
    @Column(nullable = false)
    public long price;
    @Column(nullable = false, updatable = false)
    public long entryTime;

    public long eventTime;
    public long finalPrice;

    @Transient // this prevents field to be formed in db it just remains in ram
    public Order nextOrder;

    @Transient
    public Order prevOrder;
    // we only need these for linked list not for storage

    @PrePersist
    protected void onCreate() {
        this.entryTime = System.currentTimeMillis();
    }

    public Order(String orderId, boolean buySell, long price, int shares, long entryTime, boolean marketLimit) {
        this.orderId = orderId;
        this.buySell = buySell;
        this.marketLimit = marketLimit;
        this.status = false; // by default all orders are set as pending
        this.shares = shares;
        this.price = price;
        this.entryTime = entryTime;
        this.nextOrder = null;
        this.prevOrder = null;
        this.finalPrice = 0;

    }
}
