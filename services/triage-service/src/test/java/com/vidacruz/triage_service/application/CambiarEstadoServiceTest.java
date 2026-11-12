package com.vidacruz.triage_service.application;

import com.vidacruz.triage_service.application.port.out.AuditPort;
import com.vidacruz.triage_service.application.port.out.ClockPort;
import com.vidacruz.triage_service.application.port.out.OutboxPort;
import com.vidacruz.triage_service.application.port.out.PacienteRepositoryPort;
import com.vidacruz.triage_service.application.service.CambiarEstadoService;
import com.vidacruz.triage_service.domain.exception.EstadoConcurrenteException;
import com.vidacruz.triage_service.domain.exception.PacienteNoEncontradoException;
import com.vidacruz.triage_service.domain.exception.TransicionInvalidaException;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.domain.model.TipoEvento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CambiarEstadoServiceTest {

    private static final Instant REGISTRO = Instant.parse("2026-10-29T14:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-10-29T14:03:00Z");

    @Mock PacienteRepositoryPort pacientes;
    @Mock AuditPort auditoria;
    @Mock OutboxPort outbox;

    private CambiarEstadoService service;
    private Paciente registrado;

    @BeforeEach
    void setUp() {
        ClockPort clock = () -> AHORA;
        service = new CambiarEstadoService(pacientes, auditoria, outbox, clock);
        registrado = Paciente.registrar(UUID.randomUUID(), "Carlos Gómez", 55,
                new SignosVitales(130, 180, 110, 97, 36.8, 8, 15), "Dolor torácico",
                NivelTriaje.CRITICO, 5, REGISTRO);
    }

    @Test
    void transicionValidaActualizaAuditaYPublica() {
        when(pacientes.buscarPorId(registrado.id())).thenReturn(Mono.just(registrado));
        when(pacientes.actualizarEstado(any(), eq(EstadoPaciente.REGISTRADO))).thenReturn(Mono.just(true));
        when(auditoria.registrar(any())).thenReturn(Mono.empty());
        when(outbox.guardar(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.cambiar(registrado.id(), EstadoPaciente.EN_CONSULTA)
                        .contextWrite(ctx -> ctx.put("correlationId", "cid-7")))
                .assertNext(p -> {
                    assertThat(p.estado()).isEqualTo(EstadoPaciente.EN_CONSULTA);
                    assertThat(p.fechaPrimeraConsulta()).isEqualTo(AHORA);
                })
                .verifyComplete();

        ArgumentCaptor<EventoClinico> evento = ArgumentCaptor.forClass(EventoClinico.class);
        verify(auditoria).registrar(evento.capture());
        verify(outbox).guardar(any());
        assertThat(evento.getValue().tipo()).isEqualTo(TipoEvento.PACIENTE_EN_CONSULTA);
        assertThat(evento.getValue().estadoAnterior()).isEqualTo(EstadoPaciente.REGISTRADO);
        assertThat(evento.getValue().correlationId()).isEqualTo("cid-7");
    }

    @Test
    void transicionInvalidaNoEscribeNada() {
        Paciente dadoDeAlta = registrado.cambiarEstado(EstadoPaciente.ALTA, REGISTRO);
        when(pacientes.buscarPorId(registrado.id())).thenReturn(Mono.just(dadoDeAlta));

        StepVerifier.create(service.cambiar(registrado.id(), EstadoPaciente.EN_CONSULTA))
                .expectError(TransicionInvalidaException.class)
                .verify();

        verify(pacientes, never()).actualizarEstado(any(), any());
        verifyNoInteractions(auditoria, outbox);
    }

    @Test
    void pacienteInexistenteDevuelveNotFound() {
        UUID id = UUID.randomUUID();
        when(pacientes.buscarPorId(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.cambiar(id, EstadoPaciente.EN_CONSULTA))
                .expectError(PacienteNoEncontradoException.class)
                .verify();

        verifyNoInteractions(auditoria, outbox);
    }

    @Test
    void cambioConcurrenteDevuelveConflictoSinAuditarNiPublicar() {
        when(pacientes.buscarPorId(registrado.id())).thenReturn(Mono.just(registrado));
        when(pacientes.actualizarEstado(any(), eq(EstadoPaciente.REGISTRADO))).thenReturn(Mono.just(false));

        StepVerifier.create(service.cambiar(registrado.id(), EstadoPaciente.ALTA))
                .expectError(EstadoConcurrenteException.class)
                .verify();

        verifyNoInteractions(auditoria, outbox);
    }
}
