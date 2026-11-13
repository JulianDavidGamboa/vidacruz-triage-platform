package com.vidacruz.alert_service.application.port.out;

import reactor.core.publisher.Mono;

public interface DeadLetterPort {
    Mono<Void> publicar(String clave, String valor);
}
