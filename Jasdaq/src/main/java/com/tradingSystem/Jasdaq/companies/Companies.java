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

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    /** Price set by the admin when the company was created. Never changes after creation. */
    private long initialPrice;

    /** Live market price — updated after every trade. */
    private long currentPrice;

    /** Total shares authorised for trading — set by admin, never changes after creation. */
    private int totalShares;

    /** Shares still available for new BUY orders (decremented on reservation, incremented on cancel/fill). */
    private int availableShares;

    @JsonManagedReference
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Order> orders;

    @JsonManagedReference
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Trade> tradeIds;

    /** Convenience constructor used by DataInitializer. */
    public Companies(String id, String symbol, String name, long initialPrice, int totalShares) {
        this.companyId = id;
        this.symbol = symbol;
        this.name = name;
        this.initialPrice = initialPrice;
        this.currentPrice = initialPrice;
        this.totalShares = totalShares;
        this.availableShares = totalShares;
    }

    @Override
    public String toString() {
        return "Companies{" +
                "companyId='" + companyId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", initialPrice=" + initialPrice +
                ", currentPrice=" + currentPrice +
                ", totalShares=" + totalShares +
                ", availableShares=" + availableShares +
                '}';
    }
}
