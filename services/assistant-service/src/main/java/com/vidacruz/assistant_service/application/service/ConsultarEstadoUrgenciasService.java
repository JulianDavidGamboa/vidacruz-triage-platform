package com.vidacruz.assistant_service.application.service;

import com.vidacruz.assistant_service.application.port.in.ConsultarEstadoUrgenciasUseCase;
import com.vidacruz.assistant_service.application.port.out.VistaUrgenciasPort;
import com.vidacruz.assistant_service.domain.model.EstadoUrgencias;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ConsultarEstadoUrgenciasService implements ConsultarEstadoUrgenciasUseCase {

    private final VistaUrgenciasPort vista;

    public ConsultarEstadoUrgenciasService(VistaUrgenciasPort vista) {
        this.vista = vista;
    }

    @Override
    public Mono<EstadoUrgencias> obtener() {
        return vista.leer();
    }
}
