package com.vidacruz.triage_service.application.port.in;

import com.vidacruz.triage_service.domain.model.SignosVitales;

public record RegistrarPacienteCommand(
        String nombreCompleto,
        int edad,
        SignosVitales signosVitales,
        String quejaPrincipal) {
}
