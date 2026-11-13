package com.vidacruz.triage_service.infrastructure.config;

import com.vidacruz.triage_service.domain.model.NivelTriaje;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "triage")
public record TriageProperties(
        String apiKey,
        Sla sla,
        Kafka kafka,
        @DefaultValue Outbox outbox) {

    /**
     * minutos: claves = nombre exacto del enum. evaluationInterval: cada cuánto corre el monitor.
     * monitorEnabled: permite apagar el scheduler (las pruebas de integración lo apagan y llaman al caso de uso).
     */
    public record Sla(
            Map<NivelTriaje, Integer> minutos,
            @DefaultValue("30s") Duration evaluationInterval,
            @DefaultValue("true") boolean monitorEnabled) {
    }

    public record Kafka(String bootstrapServers, @DefaultValue("triaje.eventos") String topic) {
    }

    public record Outbox(@DefaultValue("500ms") Duration pollInterval, @DefaultValue("100") int batchSize) {
    }
}

