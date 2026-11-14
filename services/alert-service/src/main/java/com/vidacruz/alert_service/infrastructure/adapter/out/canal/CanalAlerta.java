package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.domain.model.Alerta;
import reactor.core.publisher.Mono;

public interface CanalAlerta {

    String nombre();

    boolean configurado();

    Mono<Void> enviar(Alerta alerta);
}
