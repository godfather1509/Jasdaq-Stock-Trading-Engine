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
import java.util.UUID;

@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:8000"})
@RestController
@RequestMapping("/api/v1")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private com.tradingSystem.Jasdaq.Engine.TradeRepository tradeRepository;

    @Autowired
    private com.tradingSystem.Jasdaq.Engine.OrderRepository orderRepository;

    @Autowired
    private com.tradingSystem.Jasdaq.Engine.EngineService engineService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @GetMapping("/allCompanies")
    public ResponseEntity<List<Companies>> getAllCompanies() {
        return new ResponseEntity<>(companyService.allCompanies(), HttpStatus.OK);
    }

    @GetMapping("/market-stats")
    public ResponseEntity<Map<String, Object>> getMarketStats() {
        return new ResponseEntity<>(engineService.getGlobalMarketStats(), HttpStatus.OK);
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

    @GetMapping("/{id}/orders")
    public ResponseEntity<?> getOrdersByCompany(@PathVariable String id) {
        return companyService.singleCompany(id)
                .map(company -> ResponseEntity.ok(
                        orderRepository.findBySymbolOrderByEntryTimeDesc(company.getSymbol())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/trades")
    public ResponseEntity<?> getTrades(@PathVariable String id) {
        return companyService.singleCompany(id)
                .map(company -> ResponseEntity.ok(
                        tradeRepository.findBySymbolOrderByTradeTimeDesc(company.getSymbol())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Called by the Django admin panel after a new Company is saved.
     */
    @PostMapping("/companies")
    public ResponseEntity<Map<String, Object>> registerCompany(@RequestBody Companies company) {
        try {
            // Validate incoming data
            if (company.getSymbol() == null || company.getSymbol().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Symbol is required"));
            }
            if (company.getName() == null || company.getName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Company name is required"));
            }

            // Generate companyId if missing (matching Django's format)
            if (company.getCompanyId() == null || company.getCompanyId().isBlank()) {
                String symbol = company.getSymbol().toUpperCase().replaceAll("[^A-Z0-9]", "");
                company.setCompanyId(symbol + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            // availableShares tracks shares owned by users — starts at 0 (no one owns shares at IPO)
            company.setAvailableShares(0);
            if (company.getCurrentPrice() == 0) {
                company.setCurrentPrice(company.getInitialPrice());
            }

            Companies saved = companyService.registerCompany(company);

            // Place an IPO SELL order for the full share pool. Marked isIpo=true so it skips the ownership check.
            eventPublisher.publishEvent(new PlaceOrderEvent(this, false, saved.getInitialPrice(),
                    saved.getTotalShares(), false, saved.getCompanyId(), true));

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
            engineService.placeOrder(request.getBuySell(), request.getPrice(), request.getShares(),
                    request.getMarketLimit(), request.getCompanyId(), false, true);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Order placed successfully in Engine"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
