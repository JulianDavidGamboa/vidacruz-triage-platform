package com.vidacruz.assistant_service.application.service;

import com.vidacruz.assistant_service.application.port.in.ConsultarEstadoUrgenciasUseCase;
import com.vidacruz.assistant_service.application.port.out.VistaUrgenciasPort;
import com.vidacruz.assistant_service.domain.model.EstadoUrgencias;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** RF-04 parte B: lee la vista que mantiene el consumidor de triaje.eventos. No llama a la API de triaje. */
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
