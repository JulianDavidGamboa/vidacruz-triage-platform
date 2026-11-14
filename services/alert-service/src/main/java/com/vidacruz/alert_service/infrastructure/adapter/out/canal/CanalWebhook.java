package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.domain.model.Alerta;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

abstract class CanalWebhook implements CanalAlerta {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final String webhookUrl;
    private final CircuitBreaker circuitBreaker;

    CanalWebhook(WebClient.Builder builder, String webhookUrl, CircuitBreaker circuitBreaker) {
        this.webClient = builder.build();
        this.webhookUrl = webhookUrl;
        this.circuitBreaker = circuitBreaker;
    }

    abstract Object cuerpo(String texto);

    @Override
    public boolean configurado() {
        return !webhookUrl.isBlank();
    }

    @Override
    public Mono<Void> enviar(Alerta alerta) {
        return webClient.post()
                .uri(webhookUrl)
                .bodyValue(cuerpo(TextoAlerta.de(alerta)))
                .retrieve()
                .toBodilessEntity()
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .timeout(TIMEOUT)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(error -> !(error instanceof CallNotPermittedException)))
                .then();
    }
}
