package com.vidacruz.assistant_service.application.port.in;

import com.vidacruz.assistant_service.domain.model.EstadoUrgencias;
import reactor.core.publisher.Mono;

public interface ConsultarEstadoUrgenciasUseCase {
    Mono<EstadoUrgencias> obtener();
}
