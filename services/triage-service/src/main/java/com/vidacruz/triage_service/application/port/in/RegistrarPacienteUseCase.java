package com.vidacruz.triage_service.application.port.in;

import com.vidacruz.triage_service.domain.model.Paciente;
import reactor.core.publisher.Mono;

public interface RegistrarPacienteUseCase {
    Mono<Paciente> registrar(RegistrarPacienteCommand command);
}
