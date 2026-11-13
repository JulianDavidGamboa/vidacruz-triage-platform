package com.vidacruz.assistant_service.infrastructure.adapter.out.redis;

import com.vidacruz.assistant_service.application.port.out.VistaUrgenciasPort;
import com.vidacruz.assistant_service.domain.model.EstadoUrgencias;
import com.vidacruz.assistant_service.domain.model.NivelTriaje;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RedisVistaUrgenciasAdapter implements VistaUrgenciasPort {

    private static final String CLAVE = "urgencias:activos";

    private final ReactiveStringRedisTemplate redis;

    public RedisVistaUrgenciasAdapter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> activar(UUID pacienteId, NivelTriaje nivel) {
        return redis.opsForHash()
                .put(CLAVE, pacienteId.toString(), String.valueOf(nivel.codigo()))
                .then();
    }

    @Override
    public Mono<Void> desactivar(UUID pacienteId) {
        return redis.opsForHash().remove(CLAVE, pacienteId.toString()).then();
    }

    @Override
    public Mono<EstadoUrgencias> leer() {
        return redis.opsForHash().values(CLAVE)
                .map(valor -> NivelTriaje.desdeCodigo(Integer.parseInt(valor.toString())))
                .collectList()
                .map(EstadoUrgencias::de);
    }
}
