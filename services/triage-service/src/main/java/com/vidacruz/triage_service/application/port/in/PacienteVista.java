package com.vidacruz.triage_service.application.port.in;

import com.vidacruz.triage_service.domain.model.Paciente;

/** Paciente más el tiempo calculado en el momento de la consulta (dato derivado, no persistido). */
public record PacienteVista(Paciente paciente, long minutosEnEstadoActual) {
}
