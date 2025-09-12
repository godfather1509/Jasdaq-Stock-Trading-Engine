package com.tradingSystem.Jasdaq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JasdaqApplication {

	public static void main(String[] args) {
		SpringApplication.run(JasdaqApplication.class, args);
	}

}
