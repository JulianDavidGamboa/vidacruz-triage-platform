package com.vidacruz.triage_service.application.port.out;

import com.vidacruz.triage_service.domain.model.EventoClinico;
import reactor.core.publisher.Mono;

/** RN-04: la auditoría es solo de escritura (append-only); el puerto no expone borrado. */
public interface AuditPort {

    Mono<Void> registrar(EventoClinico evento);
}
