package com.vidacruz.triage_service.infrastructure.adapter.in.scheduler;

import com.vidacruz.triage_service.application.port.in.DepurarOutboxUseCase;
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

@ExtendWith(MockitoExtension.class)
class DepuracionOutboxSchedulerTest {

    private static final Duration RETENCION = Duration.ofDays(7);

    @Mock DepurarOutboxUseCase depurar;

    private VirtualTimeScheduler tiempo;
    private DepuracionOutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        tiempo = VirtualTimeScheduler.getOrSet();
        TriageProperties props = new TriageProperties("clave",
                new TriageProperties.Sla(Map.of(NivelTriaje.CRITICO, 5), Duration.ofSeconds(30), true),
                new TriageProperties.Kafka("localhost:9092", "triaje.eventos"),
                new TriageProperties.Outbox(Duration.ofMillis(500), 100, RETENCION, Duration.ofHours(1), true));
        scheduler = new DepuracionOutboxScheduler(depurar, props);
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
        VirtualTimeScheduler.reset();
    }

    @Test
    void ejecutaElCasoDeUsoConLaRetencionConfiguradaUnaVezPorIntervalo() {
        when(depurar.ejecutar(RETENCION)).thenReturn(Mono.just(0L));

        scheduler.start();
        tiempo.advanceTimeBy(Duration.ofMinutes(59));
        verify(depurar, never()).ejecutar(RETENCION);

        tiempo.advanceTimeBy(Duration.ofMinutes(2));
        verify(depurar, times(1)).ejecutar(RETENCION);

        tiempo.advanceTimeBy(Duration.ofHours(2));
        verify(depurar, times(3)).ejecutar(RETENCION);
    }

    @Test
    void unErrorNoDetieneLaDepuracion() {
        when(depurar.ejecutar(RETENCION)).thenReturn(Mono.error(new IllegalStateException("base de datos caída")),
                Mono.just(5L));

        scheduler.start();
        tiempo.advanceTimeBy(Duration.ofHours(2).plusMinutes(1));

        verify(depurar, times(2)).ejecutar(RETENCION);
    }

    @Test
    void alDetenerseDejaDeEjecutar() {
        when(depurar.ejecutar(RETENCION)).thenReturn(Mono.just(0L));

        scheduler.start();
        tiempo.advanceTimeBy(Duration.ofMinutes(61));
        scheduler.stop();
        tiempo.advanceTimeBy(Duration.ofHours(5));

        verify(depurar, times(1)).ejecutar(RETENCION);
    }
}
