package com.vidacruz.triage_service.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record PacienteResponse(
        UUID id,
        String nivel,          // CRITICO, URGENTE, URGENTE_2, NO_URGENTE, NO_URGENTE_2
        int nivelCodigo,       // 1..5
        int slaMinutos,
        String estado,         // REGISTRADO
        Instant fechaRegistro) {   // ISO 8601 (UTC)
}
