package com.vidacruz.triage_service.application;

import com.vidacruz.triage_service.application.port.out.AuditPort;
import com.vidacruz.triage_service.application.port.out.EscalamientoRepositoryPort;
import com.vidacruz.triage_service.application.port.out.MetricsPort;
import com.vidacruz.triage_service.application.port.out.OutboxPort;
import com.vidacruz.triage_service.application.service.EscalarPacientesVencidosService;
import com.vidacruz.triage_service.domain.model.Escalamiento;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.TipoEvento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalarPacientesVencidosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T14:10:00Z");

    @Mock EscalamientoRepositoryPort escalamientos;
    @Mock AuditPort auditoria;
    @Mock OutboxPort outbox;
    @Mock MetricsPort metrics;

    private EscalarPacientesVencidosService service;

    @BeforeEach
    void setUp() {
        service = new EscalarPacientesVencidosService(escalamientos, auditoria, outbox, metrics, () -> AHORA);
    }

    private static Escalamiento escalamiento(NivelTriaje nivel, int slaMinutos) {
        return new Escalamiento(UUID.randomUUID(), nivel, slaMinutos,
                AHORA.minusSeconds(slaMinutos * 60L + 120), AHORA);
    }

    @Test
    void auditaYPublicaUnEventoPorCadaPacienteEscalado() {
        Escalamiento critico = escalamiento(NivelTriaje.CRITICO, 5);
        Escalamiento urgente = escalamiento(NivelTriaje.URGENTE, 10);
        when(escalamientos.marcarVencidosComoEscalados(AHORA)).thenReturn(Flux.just(critico, urgente));
        when(auditoria.registrar(any())).thenReturn(Mono.empty());
        when(outbox.guardar(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.ejecutar().contextWrite(ctx -> ctx.put(CorrelationContext.KEY, "sla-1")))
                .expectNext(2L)
                .verifyComplete();

        ArgumentCaptor<EventoClinico> auditados = ArgumentCaptor.forClass(EventoClinico.class);
        verify(auditoria, times(2)).registrar(auditados.capture());
        verify(outbox, times(2)).guardar(any());
        verify(metrics, times(2)).escalamientoRegistrado();

        assertThat(auditados.getAllValues()).extracting(EventoClinico::pacienteId)
                .containsExactly(critico.pacienteId(), urgente.pacienteId());
        assertThat(auditados.getAllValues()).allSatisfy(e -> {
            assertThat(e.tipo()).isEqualTo(TipoEvento.PACIENTE_ESCALADO);
            assertThat(e.correlationId()).isEqualTo("sla-1");
            assertThat(e.ocurridoEn()).isEqualTo(AHORA);
        });
    }

    @Test
    void sinPacientesVencidosNoEscribeNada() {
        when(escalamientos.marcarVencidosComoEscalados(AHORA)).thenReturn(Flux.empty());

        StepVerifier.create(service.ejecutar())
                .expectNext(0L)
                .verifyComplete();

        verifyNoInteractions(auditoria, outbox, metrics);
    }

    @Test
    void consultaConElInstanteDelRelojInyectado() {
        when(escalamientos.marcarVencidosComoEscalados(AHORA)).thenReturn(Flux.empty());

        StepVerifier.create(service.ejecutar()).expectNext(0L).verifyComplete();

        verify(escalamientos).marcarVencidosComoEscalados(AHORA);
    }

    @Test
    void usaUnCorrelationIdPorDefectoSiNoHayContexto() {
        when(escalamientos.marcarVencidosComoEscalados(AHORA))
                .thenReturn(Flux.just(escalamiento(NivelTriaje.CRITICO, 5)));
        when(auditoria.registrar(any())).thenReturn(Mono.empty());
        when(outbox.guardar(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.ejecutar()).expectNext(1L).verifyComplete();

        ArgumentCaptor<EventoClinico> auditado = ArgumentCaptor.forClass(EventoClinico.class);
        verify(auditoria).registrar(auditado.capture());
        assertThat(auditado.getValue().correlationId()).isEqualTo("sla-sin-correlacion");
    }

    @Test
    void siFallaElOutboxElErrorSePropagaParaQueLaTransaccionHagaRollback() {
        when(escalamientos.marcarVencidosComoEscalados(AHORA)).thenReturn(Flux.just(
                escalamiento(NivelTriaje.CRITICO, 5), escalamiento(NivelTriaje.URGENTE, 10)));
        when(auditoria.registrar(any())).thenReturn(Mono.empty());
        when(outbox.guardar(any())).thenReturn(Mono.error(new IllegalStateException("outbox caído")));

        StepVerifier.create(service.ejecutar())
                .expectErrorMessage("outbox caído")
                .verify();

        // Se detiene en el primer fallo y no cuenta la métrica de un escalamiento que no se confirmó
        verify(outbox, times(1)).guardar(any());
        verify(metrics, never()).escalamientoRegistrado();
    }
}
