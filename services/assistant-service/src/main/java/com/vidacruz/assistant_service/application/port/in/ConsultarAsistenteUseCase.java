package com.vidacruz.assistant_service.application.port.in;

import com.vidacruz.assistant_service.domain.model.RespuestaAsistente;
import reactor.core.publisher.Mono;

public interface ConsultarAsistenteUseCase {
    Mono<RespuestaAsistente> consultar(String pregunta);
}
