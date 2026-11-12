package com.vidacruz.triage_service.application.service;

import com.vidacruz.triage_service.application.CorrelationContext;
import com.vidacruz.triage_service.application.port.in.CambiarEstadoUseCase;
import com.vidacruz.triage_service.application.port.out.AuditPort;
import com.vidacruz.triage_service.application.port.out.ClockPort;
import com.vidacruz.triage_service.application.port.out.OutboxPort;
import com.vidacruz.triage_service.application.port.out.PacienteRepositoryPort;
import com.vidacruz.triage_service.domain.exception.EstadoConcurrenteException;
import com.vidacruz.triage_service.domain.exception.PacienteNoEncontradoException;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.Paciente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class CambiarEstadoService implements CambiarEstadoUseCase {

    private final PacienteRepositoryPort pacientes;
    private final AuditPort auditoria;
    private final OutboxPort outbox;
    private final ClockPort clock;

    public CambiarEstadoService(PacienteRepositoryPort pacientes, AuditPort auditoria,
                                OutboxPort outbox, ClockPort clock) {
        this.pacientes = pacientes;
        this.auditoria = auditoria;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Lectura, transición, actualización condicional, auditoría y outbox en UNA transacción.
     * Si la transición no es válida no se escribe nada (409). Si el estado cambió concurrentemente,
     * la actualización condicional no afecta filas y se responde 409 sin auditar ni publicar.
     */
    @Override
    @Transactional
    public Mono<Paciente> cambiar(UUID pacienteId, EstadoPaciente destino) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationContext.KEY, "sin-correlacion");

            return pacientes.buscarPorId(pacienteId)
                    .switchIfEmpty(Mono.error(() -> new PacienteNoEncontradoException(pacienteId)))
                    .flatMap(actual -> {
                        Paciente nuevo = actual.cambiarEstado(destino, clock.ahora());
                        return pacientes.actualizarEstado(nuevo, actual.estado())
                                .flatMap(actualizado -> {
                                    if (!actualizado) {
                                        return Mono.<Paciente>error(new EstadoConcurrenteException(pacienteId));
                                    }
                                    EventoClinico evento = EventoClinico.cambioEstado(actual, nuevo, correlationId);
                                    return auditoria.registrar(evento)
                                            .then(outbox.guardar(evento))
                                            .thenReturn(nuevo);
                                });
                    });
        });
    }
}

