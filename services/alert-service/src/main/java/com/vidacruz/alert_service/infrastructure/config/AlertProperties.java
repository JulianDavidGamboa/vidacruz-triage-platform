package com.vidacruz.alert_service.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "alert")
public record AlertProperties(
        @DefaultValue("") String teamsWebhookUrl,
        @DefaultValue("10000") int dedupeCapacity,
        Kafka kafka) {

    public record Kafka(String bootstrapServers) {
    }
}
