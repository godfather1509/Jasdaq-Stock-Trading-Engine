package com.tradingSystem.Jasdaq.companies;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    public List<Companies> allCompanies(){
        return companyRepository.findAll();
    }

    public Optional<Companies> singleCompany(String id){
        return companyRepository.findById(id);
    }

    public Companies createCompany(Companies company){
        companyRepository.save(company);
        return company;
    }
    
}
