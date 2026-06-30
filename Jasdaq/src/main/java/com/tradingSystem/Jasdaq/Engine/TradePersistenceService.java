package com.tradingSystem.Jasdaq.Engine;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns all write-side persistence for the matching engine.
 *
 * Kept in its own bean (rather than as a method on EngineService) so the
 * {@code @Transactional} proxy is actually applied — a self-invoked transactional
 * method bypasses the Spring proxy and runs with NO transaction at all. Grouping the
 * incoming order, every resting order it touched, and every trade it produced into a
 * single {@code @Transactional} unit makes the write atomic: either all of them are
 * committed or none are, so the DB can no longer drift into a half-written state where,
 * say, the trades exist but the order that produced them does not.
 *
 * The methods run {@code @Async} on the single-threaded {@code persistenceExecutor}, so the
 * matching threads never block on DB I/O (order/metrics latency stays in µs even under a
 * write storm). The single thread + FIFO queue preserves the engine's write ordering, so
 * two writes to the same order can't be reordered into a stale state — a guarantee a
 * multi-threaded pool would lose. Trade-off: persistence is now eventually-consistent with
 * the in-memory book and the executor's queue is unbounded, so an extreme sustained backlog
 * grows in memory; revisit with batching/backpressure if that ever becomes a concern.
 */
@Service
public class TradePersistenceService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    public TradePersistenceService(OrderRepository orderRepository, TradeRepository tradeRepository) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }

    /**
     * Persists the incoming order, every resting order it affected, and every trade it
     * generated as one atomic transaction. On failure the whole unit is rolled back and
     * the error is surfaced loudly — never swallowed — because a failed write means the
     * in-memory book and the DB have diverged and need reconciliation.
     *
     * Runs on the single-threaded persistenceExecutor (not the matching thread), so the
     * engine never blocks on DB I/O; the single thread preserves write ordering.
     */
    @Async("persistenceExecutor")
    @Transactional
    public void persistTradeResults(Order incomingOrder, List<Trade> trades, List<Order> affectedOrders) {
        try {
            orderRepository.save(incomingOrder);

            if (affectedOrders != null) {
                for (Order affected : affectedOrders) {
                    orderRepository.save(affected);
                }
            }

            if (trades != null) {
                for (Trade trade : trades) {
                    tradeRepository.save(trade);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("[CRITICAL] Persistence FAILED for order " + incomingOrder.getOrderId()
                    + " with " + (trades == null ? 0 : trades.size())
                    + " trade(s) — rolling back. In-memory book and DB are now OUT OF SYNC: "
                    + e.getMessage());
            e.printStackTrace();
            throw e; // propagate so the @Transactional proxy rolls the whole unit back
        }
    }

    /**
     * Removes a cancelled order from the DB in its own transaction, off the matching
     * thread. Failures are surfaced loudly rather than swallowed.
     */
    @Async("persistenceExecutor")
    @Transactional
    public void deleteOrder(Order order) {
        try {
            orderRepository.delete(order);
        } catch (RuntimeException e) {
            System.err.println("[CRITICAL] Failed to delete cancelled order " + order.getOrderId()
                    + " — the DB may still show it as resting: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
