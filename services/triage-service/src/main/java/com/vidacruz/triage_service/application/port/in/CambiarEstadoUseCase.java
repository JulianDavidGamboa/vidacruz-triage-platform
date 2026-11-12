package com.vidacruz.triage_service.application.port.in;

import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.Paciente;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CambiarEstadoUseCase {

    /** RF-02: aplica una transición válida, audita y publica el evento. */
    Mono<Paciente> cambiar(UUID pacienteId, EstadoPaciente destino);
}
