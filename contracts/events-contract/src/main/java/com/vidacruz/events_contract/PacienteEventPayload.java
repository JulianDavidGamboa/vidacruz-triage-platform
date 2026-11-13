package com.vidacruz.events_contract;

import java.time.Instant;
import java.util.UUID;

public record PacienteEventPayload(
        UUID pacienteId,
        int nivel,
        String estadoAnterior,
        String estadoNuevo,
        Integer slaMinutos,
        Instant fechaRegistro) {
}
