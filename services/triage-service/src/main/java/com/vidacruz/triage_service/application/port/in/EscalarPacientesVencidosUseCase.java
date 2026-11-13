package com.vidacruz.triage_service.application.port.in;

import reactor.core.publisher.Mono;

public interface EscalarPacientesVencidosUseCase {

    /** Escala a los pacientes cuyo SLA venció. Devuelve cuántos escaló en esta ejecución. */
    Mono<Long> ejecutar();
}
