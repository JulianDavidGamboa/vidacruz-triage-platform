package com.vidacruz.triage_service.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    /** Todas las métricas llevan el tag application=triage-service (útil al agregar varios servicios). */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags(@Value("${spring.application.name}") String application) {
        return registry -> registry.config().commonTags("application", application);
    }
}

