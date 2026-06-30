package com.tradingSystem.Jasdaq.Engine;

import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.tradingSystem.Jasdaq.companies.Companies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
// import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Orders")
@Getter
@Setter
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
    public int initialShares;

    @Column(nullable = false)
    public long price;

    @Column(nullable = false, updatable = false)
    public long entryTime;

    @Column(nullable = false, updatable = false)
    public String symbol;

    public long eventTime;
    public long finalPrice;

    /** True for system-placed orders (IPO, relisting, admin). Cannot be cancelled from the frontend. */
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    public boolean companyOrder = false;

    @Transient
    @JsonIgnore
    public Order nextOrder;

    @Transient
    @JsonIgnore
    public Order prevOrder;

    /** Derived fill status exposed to the frontend. No DB column needed. */
    public String getFillStatus() {
        if (status) {
            // status=true with leftover shares means the order was cancelled
            // (cancel sets status=true but doesn't zero shares until EngineService does it).
            // For data persisted before that fix, treat remaining shares as CANCELLED.
            if (shares > 0 && initialShares > 0 && shares < initialShares) return "CANCELLED";
            return "FILLED";
        }
        if (initialShares > 0 && shares < initialShares) return "PARTIALLY_FILLED";
        return "PENDING";
    }

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = true)
    @JsonBackReference
    private Companies company;


    public Order(String orderId, String symbol, boolean buySell, long price, int shares, long entryTime, boolean marketLimit) {
        this.orderId = orderId;
        this.symbol=symbol;
        this.buySell = buySell;
        this.marketLimit = marketLimit;
        this.status = false;
        this.shares = shares;
        this.initialShares = shares;
        this.price = price;
        this.entryTime = entryTime;
        this.nextOrder = null;
        this.prevOrder = null;
        this.finalPrice = 0;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", buySell=" + buySell +
                ", marketLimit=" + marketLimit +
                ", status=" + status +
                ", shares=" + shares +
                ", price=" + price +
                ", entryTime=" + entryTime +
                '}';
    }
}
