package com.ovengers.gatewayservice.configs;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorHttpExchangesConfig {

    private static final int MAX_EXCHANGES_CAPACITY = 50;

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        // 메모리 사용량 제한을 위해 최대 50개의 HTTP 교환 기록만 유지
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(MAX_EXCHANGES_CAPACITY);
        return repository;
    }
}

