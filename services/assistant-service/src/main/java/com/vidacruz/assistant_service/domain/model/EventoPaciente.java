package com.vidacruz.assistant_service.domain.model;

import java.util.Objects;
import java.util.UUID;

public record EventoPaciente(UUID eventId, TipoEvento tipo, UUID pacienteId, NivelTriaje nivel) {

    public EventoPaciente {
        Objects.requireNonNull(eventId, "eventId requerido");
        Objects.requireNonNull(tipo, "tipo requerido");
        Objects.requireNonNull(pacienteId, "pacienteId requerido");
        Objects.requireNonNull(nivel, "nivel requerido");
    }
}
