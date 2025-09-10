package com.tradingSystem.Jasdaq.generator;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class IdGenerator {

    private final static AtomicLong counter=new AtomicLong(0);

    public static String nextID(String symbol, char t){
        long seq=counter.incrementAndGet();
        long date=Instant.now().getEpochSecond() * 1_000_000_000L + Instant.now().getNano();
        return symbol+"-"+date+"-"+seq+"-"+t;
    }
    
}
