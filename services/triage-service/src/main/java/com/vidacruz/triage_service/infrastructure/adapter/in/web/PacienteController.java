package com.vidacruz.triage_service.infrastructure.adapter.in.web;

import com.vidacruz.triage_service.application.port.in.CambiarEstadoUseCase;
import com.vidacruz.triage_service.application.port.in.ConsultarPacienteUseCase;
import com.vidacruz.triage_service.application.port.in.PacienteVista;
import com.vidacruz.triage_service.application.port.in.RegistrarPacienteUseCase;
import com.vidacruz.triage_service.domain.model.EstadoPaciente;
import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.CambiarEstadoRequest;
import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.PacienteDetalleResponse;
import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.PacienteResponse;
import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.RegistrarPacienteRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/triage/pacientes")
public class PacienteController {

    private final RegistrarPacienteUseCase registrarPaciente;
    private final CambiarEstadoUseCase cambiarEstado;
    private final ConsultarPacienteUseCase consultarPaciente;

    public PacienteController(RegistrarPacienteUseCase registrarPaciente,
                              CambiarEstadoUseCase cambiarEstado,
                              ConsultarPacienteUseCase consultarPaciente) {
        this.registrarPaciente = registrarPaciente;
        this.cambiarEstado = cambiarEstado;
        this.consultarPaciente = consultarPaciente;
    }

    /** Registra, clasifica y devuelve id, nivel, SLA, estado y fecha. */
    @PostMapping
    public Mono<ResponseEntity<PacienteResponse>> registrar(@Valid @RequestBody RegistrarPacienteRequest request) {
        return registrarPaciente.registrar(PacienteWebMapper.toCommand(request))
                .map(paciente -> ResponseEntity
                        .created(URI.create("/triage/pacientes/" + paciente.id()))
                        .body(PacienteWebMapper.toResponse(paciente)));
    }

    /** Transición de estado. 200 si es válida, 409 si no lo es, 404 si el paciente no existe. */
    @PatchMapping("/{id}/estado")
    public Mono<PacienteDetalleResponse> cambiarEstado(@PathVariable UUID id,
                                                       @Valid @RequestBody CambiarEstadoRequest request) {
        EstadoPaciente destino = EstadoPaciente.valueOf(request.estado());
        return cambiarEstado.cambiar(id, destino)
                .map(paciente -> PacienteWebMapper.toDetalle(new PacienteVista(paciente, 0)));
    }

    /** Datos completos, estado actual, flags (escalado) y tiempo en el estado actual. */
    @GetMapping("/{id}")
    public Mono<PacienteDetalleResponse> consultar(@PathVariable UUID id) {
        return consultarPaciente.obtener(id).map(PacienteWebMapper::toDetalle);
    }
}

