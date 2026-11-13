package com.vidacruz.triage_service.infrastructure.config;

import com.vidacruz.triage_service.application.CorrelationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdMdcTest {

    private final AtomicReference<String> enMdc = new AtomicReference<>();

    @BeforeAll
    static void activarPropagacionAutomatica() {
        Hooks.enableAutomaticContextPropagation();
    }

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    private WebTestClient cliente() {
        WebHandler handler = exchange -> Mono.just("x")
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(v -> enMdc.set(MDC.get(CorrelationContext.KEY)))
                .then();
        return WebTestClient.bindToWebHandler(handler).webFilter(new CorrelationIdWebFilter()).build();
    }

    @Test
    void elCorrelationIdRecibidoEsVisibleEnElMdcDentroDeLaCadenaReactiva() {
        cliente().get().uri("/").header(CorrelationContext.HEADER, "abc-123").exchange().expectStatus().isOk();

        assertThat(enMdc.get()).isEqualTo("abc-123");
    }

    @Test
    void sinCabeceraElMdcLlevaElIdGenerado() {
        String generado = cliente().get().uri("/").exchange().expectStatus().isOk()
                .returnResult(Void.class).getResponseHeaders().getFirst(CorrelationContext.HEADER);

        assertThat(generado).isNotBlank();
        assertThat(enMdc.get()).isEqualTo(generado);
    }

    @Test
    void elMdcNoQuedaPegadoAlHiloTrasLaPeticion() {
        cliente().get().uri("/").header(CorrelationContext.HEADER, "abc-123").exchange().expectStatus().isOk();

        assertThat(MDC.get(CorrelationContext.KEY)).isNull();
    }
}
