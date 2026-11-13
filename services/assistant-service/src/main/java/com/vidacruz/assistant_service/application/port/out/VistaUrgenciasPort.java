package com.vidacruz.assistant_service.application.port.out;

import com.vidacruz.assistant_service.domain.model.EstadoUrgencias;
import com.vidacruz.assistant_service.domain.model.NivelTriaje;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VistaUrgenciasPort {
    Mono<Void> activar(UUID pacienteId, NivelTriaje nivel);

    Mono<Void> desactivar(UUID pacienteId);

    Mono<EstadoUrgencias> leer();
}
