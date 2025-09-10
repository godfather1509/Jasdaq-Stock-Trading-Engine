package com.tradingSystem.Jasdaq.companies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tradingSystem.Jasdaq.generator.IdGenerator;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @GetMapping
    public ResponseEntity<List<Companies>> getAllCompanies()
    {
        return new ResponseEntity<List<Companies>>(companyService.allCompanies(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<Companies>> getSingleCompany(@PathVariable String id ){
        return new ResponseEntity<Optional<Companies>>(companyService.singleCompany(id), HttpStatus.OK);
    }

    @PostMapping("/post")
    public ResponseEntity<Companies> addNewCompany(@RequestBody RequestCompany request){

        String id=IdGenerator.nextID(request.getSymbol(), 'c');

        Companies company=new Companies(id,request.getSymbol(),request.getName(), request.getCurrentPrice(),request.getShares());

        Companies savedCompany=companyService.createCompany(company);
        
        return new ResponseEntity<>(savedCompany, HttpStatus.CREATED);
    }
}
