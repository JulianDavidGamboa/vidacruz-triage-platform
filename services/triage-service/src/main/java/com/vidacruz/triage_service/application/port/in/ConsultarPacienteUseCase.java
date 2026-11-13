package com.vidacruz.triage_service.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsultarPacienteUseCase {

    /** Datos completos, estado actual, flags y tiempo en el estado actual. */
    Mono<PacienteVista> obtener(UUID pacienteId);
}
