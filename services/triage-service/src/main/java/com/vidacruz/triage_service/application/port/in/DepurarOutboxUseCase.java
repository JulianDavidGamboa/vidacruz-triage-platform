package com.vidacruz.triage_service.application.port.in;

import reactor.core.publisher.Mono;

import java.time.Duration;

public interface DepurarOutboxUseCase {

    /** Elimina los eventos ya publicados con más antigüedad que la retención. Devuelve cuántas filas borró. */
    Mono<Long> ejecutar(Duration retencion);
}
