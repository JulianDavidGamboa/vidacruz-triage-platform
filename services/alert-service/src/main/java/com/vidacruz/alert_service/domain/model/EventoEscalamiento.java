package com.vidacruz.alert_service.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventoEscalamiento(
        UUID eventId,
        UUID pacienteId,
        NivelTriaje nivel,
        Instant ocurridoEn,
        Integer slaMinutos,
        Instant fechaRegistro,
        String crudo) {

    public EventoEscalamiento {
        Objects.requireNonNull(eventId, "eventId requerido");
        Objects.requireNonNull(pacienteId, "pacienteId requerido");
        Objects.requireNonNull(nivel, "nivel requerido");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn requerido");
        Objects.requireNonNull(crudo, "crudo requerido");
    }

    public Long minutosExcedidos() {
        if (slaMinutos == null || fechaRegistro == null) {
            return null;
        }
        Instant vencimiento = fechaRegistro.plus(Duration.ofMinutes(slaMinutos));
        return Math.max(0L, Duration.between(vencimiento, ocurridoEn).toMinutes());
    }
}
