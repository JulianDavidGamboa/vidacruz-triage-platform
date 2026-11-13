package com.vidacruz.triage_service.application.port.in;

import reactor.core.publisher.Mono;

import java.time.Instant;

/** Reporte de operación agrupado por hora de registro. */
public interface ReporteOperacionUseCase {

    Mono<ReporteOperacionVista> generar(Instant desde, Instant hasta);
}
