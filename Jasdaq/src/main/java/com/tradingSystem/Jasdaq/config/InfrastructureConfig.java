package com.tradingSystem.Jasdaq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class InfrastructureConfig {

    @Bean(name = "persistenceExecutor")
    public Executor persistenceExecutor() {
        // SINGLE thread on purpose: it takes DB writes off the matching threads (so the
        // engine never blocks on persistence and order/metrics latency stays in µs), while
        // still preserving the engine's write ordering. A multi-threaded pool could reorder
        // two writes to the same order and persist a stale state; one thread + FIFO queue
        // cannot. Trade-off: under extreme sustained load the write backlog can grow (the
        // queue is unbounded) — acceptable here; revisit with batching/backpressure if needed.
        return Executors.newSingleThreadExecutor();
    }

    @Bean(name = "postTradeExecutor")
    public Executor postTradeExecutor() {
        // Runs the whole post-match callback (share accounting, price update, WS/multicast
        // broadcasts, persistence hand-off) OFF the matching threads. An engine thread then
        // only ever does matching (µs) + complete(), so its queue drains fast and order/metrics
        // latency stays low. Multi-threaded because the callback's DB ops are atomic and
        // independent (commutative share increments, last-write-wins price), so they parallelise
        // safely across companies without corrupting state.
        return Executors.newFixedThreadPool(6);
    }
}
