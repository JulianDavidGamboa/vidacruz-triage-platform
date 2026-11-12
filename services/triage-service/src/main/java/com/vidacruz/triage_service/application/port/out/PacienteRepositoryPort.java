package com.vidacruz.triage_service.application.port.out;

import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.domain.model.Paciente;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PacienteRepositoryPort {

    Mono<Paciente> guardar(Paciente paciente);

    Mono<Paciente> buscarPorId(UUID id);

    /**
     * Actualiza estado, fecha de estado y primera consulta solo si el estado en base de datos sigue siendo
     * {@code estadoEsperado}. Devuelve false si otra operación cambió el estado antes (control optimista).
     */
    Mono<Boolean> actualizarEstado(Paciente nuevo, EstadoPaciente estadoEsperado);
}
