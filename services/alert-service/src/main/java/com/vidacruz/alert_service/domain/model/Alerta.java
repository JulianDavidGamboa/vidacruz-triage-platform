package com.vidacruz.alert_service.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Alerta(UUID pacienteId, NivelTriaje nivel, Instant ocurridoEn, Integer slaMinutos, Long minutosExcedidos) {

    public Alerta {
        Objects.requireNonNull(pacienteId, "pacienteId requerido");
        Objects.requireNonNull(nivel, "nivel requerido");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn requerido");
    }
}
