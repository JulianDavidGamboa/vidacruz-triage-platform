package com.vidacruz.assistant_service.application.port.out;

import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import reactor.core.publisher.Flux;

/** Recupera los fragmentos más parecidos a la pregunta. Aísla el vector store concreto (ADR-0005). */
public interface ProtocolRetrieverPort {
    Flux<FragmentoProtocolo> recuperar(String pregunta);
}
