package com.vidacruz.events_contract;

import java.util.UUID;

/**
 * Datos mínimos que necesitan los consumidores. Incluye siempre el nivel para que el asistente
 * pueda actualizar su vista sin consultar la API de triaje.
 */
public record PacienteEventPayload(
        UUID pacienteId,
        int nivel,
        String estadoAnterior,
        String estadoNuevo) {
}
