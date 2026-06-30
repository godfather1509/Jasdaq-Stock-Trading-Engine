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
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import com.tradingSystem.Jasdaq.Engine.DTO.OrderDTO1;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.MatchingEngine;
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

    /**
     * Re-seeds sell-side liquidity for a company by placing a company (treasury) SELL
     * order for the shares that are unowned but not currently on offer. This restores the
     * invariant "unowned shares == resting company sell depth", so a company whose IPO wall
     * was consumed (or whose ownership was reset) becomes buyable again.
     *
     * Places min over the books: tops the ask depth up to (totalShares - availableShares)
     * at the current price (falling back to the initial price). Idempotent-ish: if the ask
     * wall already covers the unowned shares, it places nothing.
     */
    @PostMapping("/{id}/relist")
    public CompletableFuture<ResponseEntity<Object>> relist(@PathVariable String id) {
        Companies company = companyService.getCompanyById(id);
        if (company == null) {
            return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
        }
        long price = company.getCurrentPrice() > 0 ? company.getCurrentPrice() : company.getInitialPrice();
        int unowned = company.getTotalShares() - company.getAvailableShares();

        return engineService.getMarketMetrics(id).thenApply(m -> {
            long currentAsk = (m instanceof MatchingEngine.MarketMetrics mm) ? mm.totalSellShares() : 0L;
            long toPlace = unowned - currentAsk;

            Map<String, Object> body = new HashMap<>();
            body.put("status", "ok");
            body.put("companyId", id);
            body.put("unowned", unowned);
            body.put("askDepthBefore", currentAsk);

            if (toPlace <= 0) {
                body.put("placed", 0);
                body.put("message", "Ask wall already covers unowned shares; nothing to relist.");
                return ResponseEntity.ok((Object) body);
            }

            // Company SELL order (isIpo=true) bypasses the ownership check and is uncancellable.
            engineService.placeOrder(false, price, (int) toPlace, false, id, true);
            body.put("placed", toPlace);
            body.put("price", price);
            body.put("message", "Relisted unowned shares as a company sell wall.");
            return ResponseEntity.ok((Object) body);
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

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getStats(@PathVariable String id) {
        return companyService.singleCompany(id)
                .map(company -> {
                    String sym = company.getSymbol();
                    Long qty   = tradeRepository.getTotalQuantityBySymbol(sym);
                    Long value = tradeRepository.getTotalValueBySymbol(sym);
                    Long count = tradeRepository.getTradeCountBySymbol(sym);
                    return ResponseEntity.ok(Map.of(
                            "totalTrades",       count != null ? count : 0L,
                            "totalVolume",       qty   != null ? qty   : 0L,
                            "totalValueTraded",  value != null ? value : 0L
                    ));
                })
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
                    request.getMarketLimit(), request.getCompanyId(), false, false);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Order placed successfully in Engine"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
