package com.vidacruz.triage_service.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de escalar un paciente por SLA vencido (RF-03). Lo devuelve la persistencia con
 * RETURNING, de modo que los datos del evento y de la alerta salen de la misma sentencia atómica.
 */
public record Escalamiento(
        UUID pacienteId,
        NivelTriaje nivel,
        int slaMinutos,
        Instant fechaRegistro,
        Instant fechaEscalado) {

    /** Minutos transcurridos desde que venció el SLA hasta que se escaló (útil para la alerta de RF-06). */
    public long minutosExcedidos() {
        Instant vencimiento = fechaRegistro.plus(Duration.ofMinutes(slaMinutos));
        return Math.max(0, Duration.between(vencimiento, fechaEscalado).toMinutes());
    }
}
