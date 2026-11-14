package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.infrastructure.config.AlertProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
class CanalDiscord extends CanalWebhook {

    private static final int LIMITE_CONTENIDO = 2000;

    CanalDiscord(WebClient.Builder builder, AlertProperties props,
                 @Qualifier("discordCircuitBreaker") CircuitBreaker circuitBreaker) {
        super(builder, props.discordWebhookUrl(), circuitBreaker);
    }

    @Override
    public String nombre() {
        return "Discord";
    }

    @Override
    Object cuerpo(String texto) {
        return Map.of("content", recortar(texto));
    }

    static String recortar(String texto) {
        return texto.length() <= LIMITE_CONTENIDO ? texto : texto.substring(0, LIMITE_CONTENIDO);
    }
}
