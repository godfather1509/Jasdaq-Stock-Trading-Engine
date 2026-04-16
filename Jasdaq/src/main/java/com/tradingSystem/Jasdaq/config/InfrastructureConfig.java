package com.tradingSystem.Jasdaq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class InfrastructureConfig {

    @Bean(name = "persistenceExecutor")
    public Executor persistenceExecutor() {
        // thread pool used by @Async persistence - tune size appropriately.
        return Executors.newFixedThreadPool(8);
    }
}
