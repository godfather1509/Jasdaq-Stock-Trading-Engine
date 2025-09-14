package com.tradingSystem.Jasdaq.companies;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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

    private ConcurrentHashMap<String, Companies> companyMap=new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCompanyMap() {
        for (Companies company : companyRepository.findAll()) {
            TradeEngine engine=new TradeEngine(company.getSymbol(), company.getCompanyId());
            engineMap.put(company.getCompanyId(), engine);
            companyMap.put(company.getSymbol(), company);
            engine.lob.loadOrderMap(company.getCompanyId(),orderRepository);
        }
    }

    public List<Companies> allCompanies() {
        return companyRepository.findAll();
    }

    public Optional<Companies> singleCompany(String id) {
        return companyRepository.findById(id);
    }

    public Companies createCompany(Companies company) {
        engineMap.put(company.getCompanyId(), new TradeEngine(company.getSymbol(), company.getCompanyId())); 
        // initialize new trade engine for new company
        companyMap.put(company.getSymbol(), company);
        companyRepository.save(company);
        eventPublisher.publishEvent(new PlaceOrderEvent(this,false, company.getCurrentPrice(), company.getShares(), false, company.getCompanyId()));
        return company;
    }

    public TradeEngine getTradeEngine(String companyId) {
        return engineMap.get(companyId);
    }

    public void setCurrentPrice(long price, String companyId){
        Optional<Companies> optionalCompany=companyRepository.findById(companyId);

        if(optionalCompany.isPresent()){
            Companies company=optionalCompany.get();
            company.setCurrentPrice(price);
        }
        else{
            System.out.println("Company not found");
        }
    }

    public boolean checkCompany(String symbol){
        if(companyMap.containsKey(symbol)){
            return true;
        }
        else{
            return false;
        }
    }

}
