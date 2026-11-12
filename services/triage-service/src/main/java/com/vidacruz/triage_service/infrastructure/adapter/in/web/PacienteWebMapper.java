package com.vidacruz.triage_service.infrastructure.adapter.in.web;

import com.vidacruz.triage_service.application.port.in.PacienteVista;
import com.vidacruz.triage_service.application.port.in.RegistrarPacienteCommand;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.PacienteDetalleResponse;
import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.PacienteResponse;
import com.vidacruz.triage_service.infrastructure.adapter.in.web.dto.RegistrarPacienteRequest;

final class PacienteWebMapper {

    private PacienteWebMapper() {
    }

    static RegistrarPacienteCommand toCommand(RegistrarPacienteRequest r) {
        SignosVitales signos = new SignosVitales(
                r.frecuenciaCardiaca(),
                r.presionSistolica(),
                r.presionDiastolica(),
                r.spo2(),
                r.temperatura(),
                r.dolor(),
                r.gcs());
        return new RegistrarPacienteCommand(r.nombreCompleto(), r.edad(), signos, r.quejaPrincipal());
    }

    static PacienteResponse toResponse(Paciente p) {
        return new PacienteResponse(
                p.id(),
                p.nivel().name(),
                p.nivel().codigo(),
                p.slaMinutos(),
                p.estado().name(),
                p.fechaRegistro());
    }

    static PacienteDetalleResponse toDetalle(PacienteVista vista) {
        Paciente p = vista.paciente();
        SignosVitales s = p.signosVitales();
        return new PacienteDetalleResponse(
                p.id(),
                p.nombreCompleto(),
                p.edad(),
                p.nivel().name(),
                p.nivel().codigo(),
                p.slaMinutos(),
                p.estado().name(),
                vista.minutosEnEstadoActual(),
                new PacienteDetalleResponse.Flags(p.escalado()),
                new PacienteDetalleResponse.SignosVitalesResponse(s.frecuenciaCardiaca(), s.presionSistolica(), s.presionDiastolica(),
                        s.spo2(), s.temperatura(), s.dolor(), s.gcs()),
                p.quejaPrincipal(),
                p.fechaRegistro(),
                p.fechaEstado(),
                p.fechaEscalado(),
                p.fechaPrimeraConsulta());
    }
}

