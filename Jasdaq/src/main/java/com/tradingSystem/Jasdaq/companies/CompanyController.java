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
import java.util.List;
import java.util.Map;
import com.tradingSystem.Jasdaq.Engine.DTO.OrderDTO1;
import org.springframework.context.ApplicationEventPublisher;

@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:8000"})
@RestController
@RequestMapping("/api/v1")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private com.tradingSystem.Jasdaq.Engine.EngineService engineService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @GetMapping("/allCompanies")
    public ResponseEntity<List<Companies>> getAllCompanies() {
        return new ResponseEntity<>(companyService.allCompanies(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Companies> getSingleCompany(@PathVariable String id) {
        return companyService.singleCompany(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/metrics")
    public java.util.concurrent.CompletableFuture<ResponseEntity<Object>> getMetrics(@PathVariable String id) {
        return engineService.getMarketMetrics(id)
                .thenApply(metrics -> {
                    if (metrics == null) return ResponseEntity.notFound().build();
                    return ResponseEntity.ok(metrics);
                });
    }

    /**
     * Called by the Django admin panel after a new Company is saved.
     */
    @PostMapping("/companies")
    public ResponseEntity<Map<String, Object>> registerCompany(@RequestBody Companies company) {
        try {
            if (company.getAvailableShares() == 0) {
                company.setAvailableShares(company.getTotalShares());
            }
            if (company.getCurrentPrice() == 0) {
                company.setCurrentPrice(company.getInitialPrice());
            }
            Companies saved = companyService.registerCompany(company);

            // Automatically place an IPO SELL order for the entire totalShares pool at the initial price.
            // This ensures that the exact number of issued shares are available on the market for trading.
            eventPublisher.publishEvent(new PlaceOrderEvent(this, false, saved.getInitialPrice(),
                    saved.getTotalShares(), false, saved.getCompanyId()));

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "companyId", saved.getCompanyId(),
                    "symbol", saved.getSymbol(),
                    "totalShares", saved.getTotalShares(),
                    "availableShares", saved.getAvailableShares()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> placeOrderRest(@RequestBody OrderDTO1 request) {
        try {
            engineService.placeOrder(request.isBuySell(), request.getPrice(), request.getShares(),
                    request.isMarketLimit(), request.getCompanyId());
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Order placed successfully in Engine"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
