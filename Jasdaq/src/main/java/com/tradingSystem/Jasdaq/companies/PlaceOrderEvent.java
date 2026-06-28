package com.tradingSystem.Jasdaq.companies;

import org.springframework.context.ApplicationEvent;

public class PlaceOrderEvent extends ApplicationEvent {

    boolean buySell;
    long price;
    int shares;
    boolean marketLimit;
    String companyId;
    boolean ipo;

    public PlaceOrderEvent(Object source, boolean buySell, long price, int shares, boolean marketLimit, String companyId, boolean ipo) {
        super(source);
        this.buySell = buySell;
        this.price = price;
        this.shares = shares;
        this.marketLimit = marketLimit;
        this.companyId = companyId;
        this.ipo = ipo;
    }

    public boolean getBuySell()    { return buySell; }
    public long getPrice()         { return price; }
    public int getShares()         { return shares; }
    public boolean getMarketLimit(){ return marketLimit; }
    public String getCompanyId()   { return companyId; }
    public boolean isIpo()         { return ipo; }
}
