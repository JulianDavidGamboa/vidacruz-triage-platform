package com.vidacruz.triage_service.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Respuesta de GET y PATCH: datos completos del paciente, estado actual y flags. */
public record PacienteDetalleResponse(
        UUID id,
        String nombreCompleto,
        int edad,
        String nivel,
        int nivelCodigo,
        int slaMinutos,
        String estado,
        long minutosEnEstadoActual,
        Flags flags,
        SignosVitalesResponse signosVitales,
        String quejaPrincipal,
        Instant fechaRegistro,
        Instant fechaEstado,
        Instant fechaEscalado,
        Instant fechaPrimeraConsulta) {

    public record Flags(boolean escalado) {
    }

    public record SignosVitalesResponse(
            int frecuenciaCardiaca,
            int presionSistolica,
            int presionDiastolica,
            int spo2,
            double temperatura,
            int dolor,
            int gcs) {
    }
}

