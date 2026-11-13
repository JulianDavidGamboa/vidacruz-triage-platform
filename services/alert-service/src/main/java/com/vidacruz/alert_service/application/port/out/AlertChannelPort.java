package com.vidacruz.alert_service.application.port.out;

import com.vidacruz.alert_service.domain.model.Alerta;
import com.vidacruz.alert_service.domain.model.ResultadoEnvio;
import reactor.core.publisher.Mono;

public interface AlertChannelPort {
    Mono<ResultadoEnvio> enviar(Alerta alerta);
}
