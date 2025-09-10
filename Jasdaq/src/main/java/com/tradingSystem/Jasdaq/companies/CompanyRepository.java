package com.tradingSystem.Jasdaq.companies;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Companies, String> // 2nd argument here is datatype of  primary key
{
    
}
