package com.vidacruz.triage_service.application.port.out;

import com.vidacruz.triage_service.domain.model.EventoClinico;
import reactor.core.publisher.Mono;

import java.time.Instant;

/** Deja el evento en la tabla outbox, dentro de la misma transacción que el cambio de estado. */
public interface OutboxPort {

    Mono<Void> guardar(EventoClinico evento);

    /** Borra solo las filas ya publicadas antes del límite; las pendientes nunca se tocan. Devuelve cuántas borró. */
    Mono<Long> eliminarPublicadosAntesDe(Instant limite);
}
