package com.vidacruz.assistant_service.application.port.out;

import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import reactor.core.publisher.Mono;

import java.util.List;

/** Genera la respuesta a partir solo del contexto recuperado. */
public interface LlmPort {
    Mono<String> responder(String pregunta, List<FragmentoProtocolo> contexto);
}
