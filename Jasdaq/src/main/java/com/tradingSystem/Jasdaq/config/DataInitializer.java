package com.tradingSystem.Jasdaq.config;

import com.tradingSystem.Jasdaq.companies.Companies;
import com.tradingSystem.Jasdaq.companies.CompanyRepository;
import com.tradingSystem.Jasdaq.generator.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private CompanyRepository companyRepository;

    @Override
    public void run(String... args) throws Exception {
        if (companyRepository.count() == 0) {
            List<Companies> initialCompanies = Arrays.asList(
                new Companies(IdGenerator.nextID("AAPL", 'c'), "AAPL", "Apple Inc.", 15000, 1000000),
                new Companies(IdGenerator.nextID("GOOGL", 'c'), "GOOGL", "Alphabet Inc.", 280000, 500000),
                new Companies(IdGenerator.nextID("MSFT", 'c'), "MSFT", "Microsoft Corp.", 30000, 800000),
                new Companies(IdGenerator.nextID("TSLA", 'c'), "TSLA", "Tesla Inc.", 70000, 300000),
                new Companies(IdGenerator.nextID("AMZN", 'c'), "AMZN", "Amazon.com Inc.", 330000, 400000)
            );
            companyRepository.saveAll(initialCompanies);
            System.out.println("Initial company data seeded.");
        }
    }
}
