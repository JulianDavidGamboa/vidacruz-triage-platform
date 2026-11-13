package com.vidacruz.assistant_service.application.port.out;

import com.vidacruz.assistant_service.domain.model.FragmentoProtocolo;
import reactor.core.publisher.Mono;

import java.util.List;

public interface LlmPort {
    Mono<String> responder(String pregunta, List<FragmentoProtocolo> contexto);
}
