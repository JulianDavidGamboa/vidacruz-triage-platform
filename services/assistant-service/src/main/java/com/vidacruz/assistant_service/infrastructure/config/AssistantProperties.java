package com.vidacruz.assistant_service.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(String apiKey, Rag rag, Kafka kafka) {

    /** topK y umbral de similitud de la compuerta anti-alucinación (ADR-0005). */
    public record Rag(@DefaultValue("3") int topK, @DefaultValue("0.75") double similarityThreshold) {
    }

    public record Kafka(String bootstrapServers, @DefaultValue("triaje.eventos") String topic) {
    }
}
