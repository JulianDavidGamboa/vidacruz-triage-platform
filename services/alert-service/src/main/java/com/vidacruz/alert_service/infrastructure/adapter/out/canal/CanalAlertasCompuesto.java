package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.application.port.out.AlertChannelPort;
import com.vidacruz.alert_service.domain.model.Alerta;
import com.vidacruz.alert_service.domain.model.ResultadoEnvio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Primary
public class CanalAlertasCompuesto implements AlertChannelPort {

    private static final Logger log = LoggerFactory.getLogger(CanalAlertasCompuesto.class);
    private static final Marker ALERTA = MarkerFactory.getMarker("ALERTA_ESCALAMIENTO");

    private final List<CanalAlerta> canales;

    public CanalAlertasCompuesto(List<CanalAlerta> canales) {
        this.canales = canales;
    }

    @Override
    public Mono<ResultadoEnvio> enviar(Alerta alerta) {
        List<CanalAlerta> activos = canales.stream().filter(CanalAlerta::configurado).toList();
        if (activos.isEmpty()) {
            log.warn(ALERTA, "Sin webhook configurado; alerta solo en log: {}", TextoAlerta.de(alerta));
            return Mono.just(ResultadoEnvio.SOLO_LOG);
        }
        return Flux.fromIterable(activos)
                .flatMap(canal -> entregar(canal, alerta))
                .collectList()
                .map(resultados -> resultados.contains(Boolean.TRUE)
                        ? ResultadoEnvio.ENVIADA
                        : ResultadoEnvio.REGISTRADA_EN_LOG);
    }

    private Mono<Boolean> entregar(CanalAlerta canal, Alerta alerta) {
        return Mono.defer(() -> canal.enviar(alerta))
                .thenReturn(true)
                .onErrorResume(error -> {
                    log.warn(ALERTA, "{} no disponible ({}); alerta registrada en log de respaldo: {}",
                            canal.nombre(), error.getClass().getSimpleName(), TextoAlerta.de(alerta));
                    return Mono.just(false);
                });
    }
}
