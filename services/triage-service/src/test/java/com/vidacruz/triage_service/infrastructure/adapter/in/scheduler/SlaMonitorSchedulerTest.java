package com.vidacruz.triage_service.infrastructure.adapter.in.scheduler;

import com.vidacruz.triage_service.application.port.in.EscalarPacientesVencidosUseCase;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.infrastructure.config.TriageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Usa tiempo virtual: simula minutos de operación sin esperar. Está en el mismo paquete para invocar start(). */
@ExtendWith(MockitoExtension.class)
class SlaMonitorSchedulerTest {

    @Mock EscalarPacientesVencidosUseCase escalar;

    private VirtualTimeScheduler tiempo;
    private SlaMonitorScheduler scheduler;

    @BeforeEach
    void setUp() {
        tiempo = VirtualTimeScheduler.getOrSet();   // antes de crear el Flux.interval del scheduler
        TriageProperties props = new TriageProperties("clave",
                new TriageProperties.Sla(Map.of(NivelTriaje.CRITICO, 5), Duration.ofSeconds(30), true),
                new TriageProperties.Kafka("localhost:9092", "triaje.eventos"),
                new TriageProperties.Outbox(Duration.ofMillis(500), 100));
        scheduler = new SlaMonitorScheduler(escalar, props);
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
        VirtualTimeScheduler.reset();
    }

    @Test
    void ejecutaElCasoDeUsoUnaVezPorIntervalo() {
        when(escalar.ejecutar()).thenReturn(Mono.just(0L));

        scheduler.start();
        tiempo.advanceTimeBy(Duration.ofSeconds(29));
        verify(escalar, never()).ejecutar();

        tiempo.advanceTimeBy(Duration.ofSeconds(2));
        verify(escalar, times(1)).ejecutar();

        tiempo.advanceTimeBy(Duration.ofSeconds(60));
        verify(escalar, times(3)).ejecutar();
    }

    @Test
    void unErrorNoDetieneElMonitor() {
        when(escalar.ejecutar()).thenReturn(Mono.error(new IllegalStateException("base de datos caída")),
                Mono.just(1L));

        scheduler.start();
        tiempo.advanceTimeBy(Duration.ofSeconds(61));

        verify(escalar, times(2)).ejecutar();
    }

    @Test
    void alDetenerseDejaDeEjecutar() {
        when(escalar.ejecutar()).thenReturn(Mono.just(0L));

        scheduler.start();
        tiempo.advanceTimeBy(Duration.ofSeconds(31));
        scheduler.stop();
        tiempo.advanceTimeBy(Duration.ofSeconds(120));

        verify(escalar, times(1)).ejecutar();
    }
}
