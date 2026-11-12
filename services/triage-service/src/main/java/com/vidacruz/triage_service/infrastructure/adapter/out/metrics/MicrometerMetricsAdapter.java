package com.vidacruz.triage_service.infrastructure.adapter.out.metrics;

import com.vidacruz.triage_service.application.port.out.MetricsPort;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Métricas del CLAUDE.md del proyecto: triage.clasificacion{nivel} y triage.escalamientos. */
@Component
public class MicrometerMetricsAdapter implements MetricsPort {

    private final MeterRegistry registry;

    public MicrometerMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void clasificacionRegistrada(NivelTriaje nivel) {
        registry.counter("triage.clasificacion", "nivel", nivel.name()).increment();
    }

    @Override
    public void escalamientoRegistrado() {
        registry.counter("triage.escalamientos").increment();
    }
}
