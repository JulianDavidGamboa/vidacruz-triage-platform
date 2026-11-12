package com.vidacruz.triage_service.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de PATCH /triage/pacientes/{id}/estado. Cualquier otro valor → 400. */
public record CambiarEstadoRequest(
        @NotBlank(message = "estado es obligatorio")
        @Pattern(regexp = "REGISTRADO|EN_CONSULTA|ALTA",
                message = "estado debe ser REGISTRADO, EN_CONSULTA o ALTA")
        String estado) {
}