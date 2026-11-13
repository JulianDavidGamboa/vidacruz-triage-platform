package com.vidacruz.assistant_service.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    CircuitBreaker llmCircuitBreaker() {
        return CircuitBreaker.of("llm", configuracion());
    }

    @Bean
    CircuitBreaker embeddingsCircuitBreaker() {
        return CircuitBreaker.of("embeddings", configuracion());
    }

    private static CircuitBreakerConfig configuracion() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
    }
}
