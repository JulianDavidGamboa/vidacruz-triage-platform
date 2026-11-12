package com.vidacruz.triage_service.application;

import com.vidacruz.triage_service.application.port.in.RegistrarPacienteCommand;
import com.vidacruz.triage_service.application.port.out.AuditPort;
import com.vidacruz.triage_service.application.port.out.ClockPort;
import com.vidacruz.triage_service.application.port.out.MetricsPort;
import com.vidacruz.triage_service.application.port.out.OutboxPort;
import com.vidacruz.triage_service.application.port.out.PacienteRepositoryPort;
import com.vidacruz.triage_service.application.port.out.SlaPolicy;
import com.vidacruz.triage_service.application.service.RegistrarPacienteService;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.domain.model.TipoEvento;
import com.vidacruz.triage_service.domain.service.TriageClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarPacienteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T14:00:00Z");

    @Mock PacienteRepositoryPort pacientes;
    @Mock AuditPort auditoria;
    @Mock OutboxPort outbox;
    @Mock MetricsPort metrics;

    private final SlaPolicy sla = nivel -> switch (nivel) {
        case CRITICO -> 5;
        case URGENTE -> 10;
        case URGENTE_2 -> 30;
        case NO_URGENTE -> 60;
        case NO_URGENTE_2 -> 120;
    };

    private static ClockPort fijo(Instant instante) {
        return () -> instante;
    }

    private RegistrarPacienteService service;

    @BeforeEach
    void setUp() {
        service = new RegistrarPacienteService(new TriageClassifier(), sla, pacientes, auditoria, outbox,
                metrics, fijo(AHORA));
    }

    private static RegistrarPacienteCommand comandoE1() {
        return new RegistrarPacienteCommand(
                "  Carlos Gómez  ", 55,
                new SignosVitales(130, 180, 110, 97, 36.8, 8, 15),
                "Dolor torácico");
    }

    @Test
    void registraClasificaAuditaYPublicaEnElOutbox() {
        when(pacientes.guardar(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(auditoria.registrar(any())).thenReturn(Mono.empty());
        when(outbox.guardar(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.registrar(comandoE1())
                        .contextWrite(ctx -> ctx.put(CorrelationContext.KEY, "cid-123")))
                .assertNext(p -> {
                    assertThat(p.id()).isNotNull();
                    assertThat(p.nombreCompleto()).isEqualTo("Carlos Gómez");
                    assertThat(p.nivel()).isEqualTo(NivelTriaje.CRITICO);
                    assertThat(p.slaMinutos()).isEqualTo(5);
                    assertThat(p.estado()).isEqualTo(EstadoPaciente.REGISTRADO);
                    assertThat(p.escalado()).isFalse();
                    assertThat(p.fechaRegistro()).isEqualTo(AHORA);
                })
                .verifyComplete();

        ArgumentCaptor<EventoClinico> auditado = ArgumentCaptor.forClass(EventoClinico.class);
        ArgumentCaptor<EventoClinico> publicado = ArgumentCaptor.forClass(EventoClinico.class);
        verify(auditoria).registrar(auditado.capture());
        verify(outbox).guardar(publicado.capture());
        verify(metrics).clasificacionRegistrada(NivelTriaje.CRITICO);

        // Mismo hecho clínico para auditoría y evento
        assertThat(auditado.getValue()).isEqualTo(publicado.getValue());
        EventoClinico evento = publicado.getValue();
        assertThat(evento.tipo()).isEqualTo(TipoEvento.PACIENTE_REGISTRADO);
        assertThat(evento.estadoAnterior()).isNull();
        assertThat(evento.estadoNuevo()).isEqualTo(EstadoPaciente.REGISTRADO);
        assertThat(evento.nivel()).isEqualTo(NivelTriaje.CRITICO);
        assertThat(evento.correlationId()).isEqualTo("cid-123");
        assertThat(evento.ocurridoEn()).isEqualTo(AHORA);
    }

    @Test
    void usaElSlaConfiguradoParaElNivelClasificado() {
        when(pacientes.guardar(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(auditoria.registrar(any())).thenReturn(Mono.empty());
        when(outbox.guardar(any())).thenReturn(Mono.empty());
        // E5: renovación de recetario con signos normales → NO_URGENTE_2 (120 min)
        RegistrarPacienteCommand e5 = new RegistrarPacienteCommand(
                "Laura Díaz", 30, new SignosVitales(70, 115, 75, 99, 36.4, 0, 15), "Renovación de recetario");

        StepVerifier.create(service.registrar(e5))
                .assertNext((Paciente p) -> {
                    assertThat(p.nivel()).isEqualTo(NivelTriaje.NO_URGENTE_2);
                    assertThat(p.slaMinutos()).isEqualTo(120);
                })
                .verifyComplete();
    }

    @Test
    void siFallaElOutboxElErrorSePropagaYNoSeCuentaLaMetrica() {
        when(pacientes.guardar(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(auditoria.registrar(any())).thenReturn(Mono.empty());
        when(outbox.guardar(any())).thenReturn(Mono.error(new IllegalStateException("outbox caído")));

        StepVerifier.create(service.registrar(comandoE1()))
                .expectErrorMessage("outbox caído")
                .verify();

        verify(metrics, never()).clasificacionRegistrada(any());
    }
}
