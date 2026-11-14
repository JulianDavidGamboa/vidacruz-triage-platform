package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.infrastructure.config.AlertProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
class CanalTeams extends CanalWebhook {

    CanalTeams(WebClient.Builder builder, AlertProperties props,
               @Qualifier("teamsCircuitBreaker") CircuitBreaker circuitBreaker) {
        super(builder, props.teamsWebhookUrl(), circuitBreaker);
    }

    @Override
    public String nombre() {
        return "Teams";
    }

    @Override
    Object cuerpo(String texto) {
        return Map.of("text", texto);
    }
}
