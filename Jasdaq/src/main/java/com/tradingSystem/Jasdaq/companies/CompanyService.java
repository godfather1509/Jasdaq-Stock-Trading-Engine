package com.tradingSystem.Jasdaq.companies;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tradingSystem.Jasdaq.Engine.matchingEngine.TradeEngine;

import jakarta.annotation.PostConstruct;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    private final ConcurrentHashMap<String, TradeEngine> companyMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCompanyMap() {
        for (Companies company : companyRepository.findAll()) {
            companyMap.put(company.getCompanyId(), new TradeEngine(company.getSymbol(), company.getCompanyId()));
        }
    }

    public List<Companies> allCompanies() {
        return companyRepository.findAll();
    }

    public Optional<Companies> singleCompany(String id) {
        return companyRepository.findById(id);
    }

    public Companies createCompany(Companies company) {
        companyMap.put(company.getCompanyId(), new TradeEngine(company.getSymbol(), company.getCompanyId()));
        TradeEngine engine = companyMap.get(company.getCompanyId());
        engine.submitAddRequest(false, company.getCurrentPrice(), company.getShares(), false);
        companyRepository.save(company);
        return company;
    }

    public TradeEngine getTradeEngine(String companyId) {
        return companyMap.get(companyId);
    }

}
