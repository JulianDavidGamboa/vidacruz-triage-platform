package com.vidacruz.assistant_service.application.service;

import com.vidacruz.assistant_service.application.port.in.ProcesarEventoPacienteUseCase;
import com.vidacruz.assistant_service.application.port.out.VistaUrgenciasPort;
import com.vidacruz.assistant_service.domain.model.EventoPaciente;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Traduce cada evento a la vista. Un paciente está activo desde el registro hasta el alta: escalar no lo cambia,
 * porque el escalamiento es un flag y el estado sigue en REGISTRADO.
 */
@Service
public class ProcesarEventoPacienteService implements ProcesarEventoPacienteUseCase {

    private final VistaUrgenciasPort vista;

    public ProcesarEventoPacienteService(VistaUrgenciasPort vista) {
        this.vista = vista;
    }

    @Override
    public Mono<Void> procesar(EventoPaciente evento) {
        return switch (evento.tipo()) {
            case PACIENTE_REGISTRADO, PACIENTE_EN_CONSULTA -> vista.activar(evento.pacienteId(), evento.nivel());
            case PACIENTE_ALTA -> vista.desactivar(evento.pacienteId());
            case PACIENTE_ESCALADO -> Mono.empty();
        };
    }
}
