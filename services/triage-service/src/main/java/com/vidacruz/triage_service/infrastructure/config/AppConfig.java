package com.vidacruz.triage_service.infrastructure.config;

import com.vidacruz.triage_service.domain.service.TriageClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Reloj inyectable: permite fijar el tiempo en los tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** El dominio no conoce Spring; el bean se declara aquí. */
    @Bean
    TriageClassifier triageClassifier() {
        return new TriageClassifier();
    }

    @Bean
    TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}


