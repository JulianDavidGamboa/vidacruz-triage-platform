package com.vidacruz.triage_service.application.service;

import com.vidacruz.triage_service.application.port.in.ConsultarPacienteUseCase;
import com.vidacruz.triage_service.application.port.in.PacienteVista;
import com.vidacruz.triage_service.application.port.out.ClockPort;
import com.vidacruz.triage_service.application.port.out.PacienteRepositoryPort;
import com.vidacruz.triage_service.domain.exception.PacienteNoEncontradoException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ConsultarPacienteService implements ConsultarPacienteUseCase {

    private final PacienteRepositoryPort pacientes;
    private final ClockPort clock;

    public ConsultarPacienteService(PacienteRepositoryPort pacientes, ClockPort clock) {
        this.pacientes = pacientes;
        this.clock = clock;
    }

    @Override
    public Mono<PacienteVista> obtener(UUID pacienteId) {
        return pacientes.buscarPorId(pacienteId)
                .switchIfEmpty(Mono.error(() -> new PacienteNoEncontradoException(pacienteId)))
                .map(p -> new PacienteVista(p, p.minutosEnEstadoActual(clock.ahora())));
    }
}

