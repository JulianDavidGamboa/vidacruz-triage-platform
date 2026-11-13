package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.application.port.out.AlertChannelPort;
import com.vidacruz.alert_service.domain.model.Alerta;
import com.vidacruz.alert_service.domain.model.ResultadoEnvio;
import com.vidacruz.alert_service.infrastructure.config.AlertProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Component
public class CanalAlertasAdapter implements AlertChannelPort {

    private static final Logger log = LoggerFactory.getLogger(CanalAlertasAdapter.class);
    private static final Marker ALERTA = MarkerFactory.getMarker("ALERTA_ESCALAMIENTO");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final String webhookUrl;
    private final CircuitBreaker teams;

    public CanalAlertasAdapter(WebClient.Builder builder, AlertProperties props,
                               @Qualifier("teamsCircuitBreaker") CircuitBreaker teams) {
        this.webClient = builder.build();
        this.webhookUrl = props.teamsWebhookUrl();
        this.teams = teams;
    }

    @Override
    public Mono<ResultadoEnvio> enviar(Alerta alerta) {
        String texto = texto(alerta);
        if (webhookUrl.isBlank()) {
            log.warn(ALERTA, "Sin webhook configurado; alerta solo en log: {}", texto);
            return Mono.just(ResultadoEnvio.SOLO_LOG);
        }
        return webClient.post()
                .uri(webhookUrl)
                .bodyValue(Map.of("text", texto))
                .retrieve()
                .toBodilessEntity()
                .transformDeferred(CircuitBreakerOperator.of(teams))
                .timeout(TIMEOUT)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(error -> !(error instanceof CallNotPermittedException)))
                .map(respuesta -> ResultadoEnvio.ENVIADA)
                .onErrorResume(error -> {
                    log.warn(ALERTA, "Teams no disponible; alerta registrada en log de respaldo: {}", texto, error);
                    return Mono.just(ResultadoEnvio.REGISTRADA_EN_LOG);
                });
    }

    private static String texto(Alerta alerta) {
        String base = "Escalamiento de paciente %s: nivel %s (código %d)"
                .formatted(alerta.pacienteId(), alerta.nivel().name(), alerta.nivel().codigo());
        if (alerta.slaMinutos() != null && alerta.minutosExcedidos() != null) {
            base += ", SLA %d min, excedido %d min".formatted(alerta.slaMinutos(), alerta.minutosExcedidos());
        }
        return base + " desde %s. Consulte el detalle en la API de triaje.".formatted(alerta.ocurridoEn());
    }
}
