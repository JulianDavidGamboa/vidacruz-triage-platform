package com.vidacruz.alert_service.application.port.in;

import com.vidacruz.alert_service.domain.model.EventoEscalamiento;
import reactor.core.publisher.Mono;

public interface ProcesarEscalamientoUseCase {
    Mono<Void> procesar(EventoEscalamiento evento);
}
