package com.tradingSystem.Jasdaq.companies;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradingSystem.Jasdaq.Engine.OrderRepository;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.TradeEngine;

import jakarta.annotation.PostConstruct;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private ConcurrentHashMap<String, TradeEngine> engineMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Companies> companyMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCompanyMap() {
        for (Companies company : companyRepository.findAll()) {
            TradeEngine engine = new TradeEngine(company.getSymbol(), company.getCompanyId());
            engineMap.put(company.getCompanyId(), engine);
            companyMap.put(company.getSymbol(), company);
            engine.lob.loadOrderMap(company.getCompanyId(), orderRepository);
        }
    }

    public List<Companies> allCompanies() {
        try {
            List<Companies> result = companyRepository.findAll();
            System.out.println("Returning " + result.size() + " companies.");
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public Optional<Companies> singleCompany(String id) {
        return companyRepository.findById(id);
    }

    public Companies getCompanyById(String companyId) {
        return companyRepository.findById(companyId).orElse(null);
    }

    /**
     * Registers a company that was created externally (e.g., by the Django admin panel).
     * The company object must already be fully populated (companyId, symbol, name,
     * totalShares, availableShares, initialPrice, currentPrice).
     * Only saves to DB if not already present, then registers the TradeEngine in memory.
     */
    public Companies registerCompany(Companies company) {
        if (!companyRepository.existsById(company.getCompanyId())) {
            companyRepository.save(company);
        }
        engineMap.putIfAbsent(company.getCompanyId(),
                new TradeEngine(company.getSymbol(), company.getCompanyId()));
        companyMap.put(company.getSymbol(), company);
        System.out.println("[INFO] Registered TradeEngine for " + company.getSymbol());
        return company;
    }

    /** @deprecated Use registerCompany() for externally-created companies. */
    public Companies createCompany(Companies company) {
        engineMap.put(company.getCompanyId(), new TradeEngine(company.getSymbol(), company.getCompanyId()));
        companyMap.put(company.getSymbol(), company);
        companyRepository.save(company);
        eventPublisher.publishEvent(new PlaceOrderEvent(this, false, company.getCurrentPrice(),
                company.getTotalShares(), false, company.getCompanyId()));
        return company;
    }

    public TradeEngine getTradeEngine(String companyId) {
        return engineMap.get(companyId);
    }

    public void setCurrentPrice(long price, String companyId) {
        Optional<Companies> optionalCompany = companyRepository.findById(companyId);
        if (optionalCompany.isPresent()) {
            Companies company = optionalCompany.get();
            company.setCurrentPrice(price);
            companyRepository.save(company);
        } else {
            System.out.println("Company not found");
        }
    }

    public boolean checkCompany(String symbol) {
        return companyMap.containsKey(symbol);
    }

    public Companies getCompanyBySymbol(String symbol) {
        return companyMap.get(symbol);
    }

    // Share-pool management removed: shares are enforced organically by the initial IPO Order.
}
