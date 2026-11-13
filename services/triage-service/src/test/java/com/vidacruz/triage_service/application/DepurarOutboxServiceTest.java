package com.vidacruz.triage_service.application;

import com.vidacruz.triage_service.application.port.out.OutboxPort;
import com.vidacruz.triage_service.application.service.DepurarOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepurarOutboxServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-13T12:00:00Z");

    @Mock OutboxPort outbox;

    private DepurarOutboxService service;

    @BeforeEach
    void setUp() {
        service = new DepurarOutboxService(outbox, () -> AHORA);
    }

    @Test
    void eliminaLosPublicadosAnterioresAlLimiteCalculadoConElRelojInyectado() {
        Instant limite = Instant.parse("2026-11-06T12:00:00Z");
        when(outbox.eliminarPublicadosAntesDe(limite)).thenReturn(Mono.just(42L));

        StepVerifier.create(service.ejecutar(Duration.ofDays(7)))
                .expectNext(42L)
                .verifyComplete();

        verify(outbox).eliminarPublicadosAntesDe(limite);
    }

    @Test
    void sinFilasParaEliminarDevuelveCero() {
        when(outbox.eliminarPublicadosAntesDe(AHORA.minus(Duration.ofHours(1)))).thenReturn(Mono.just(0L));

        StepVerifier.create(service.ejecutar(Duration.ofHours(1)))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void propagaElErrorDelPuerto() {
        when(outbox.eliminarPublicadosAntesDe(AHORA.minus(Duration.ofDays(7))))
                .thenReturn(Mono.error(new IllegalStateException("base de datos caída")));

        StepVerifier.create(service.ejecutar(Duration.ofDays(7)))
                .expectErrorMessage("base de datos caída")
                .verify();
    }
}
