package com.vidacruz.triage_service.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsultarPacienteUseCase {

    /** RF-02: datos completos, estado actual, flags y tiempo en el estado actual. */
    Mono<PacienteVista> obtener(UUID pacienteId);
}
