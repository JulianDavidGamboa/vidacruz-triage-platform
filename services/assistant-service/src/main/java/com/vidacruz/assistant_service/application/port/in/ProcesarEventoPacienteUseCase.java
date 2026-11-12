package com.vidacruz.assistant_service.application.port.in;

import com.vidacruz.assistant_service.domain.model.EventoPaciente;
import reactor.core.publisher.Mono;

public interface ProcesarEventoPacienteUseCase {
    Mono<Void> procesar(EventoPaciente evento);
}
