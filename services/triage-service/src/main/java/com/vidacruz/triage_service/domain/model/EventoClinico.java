package com.vidacruz.triage_service.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Hecho clínico ocurrido (registro, consulta, escalamiento, alta). Alimenta tanto la auditoría (RN-04)
 * como el evento de integración que se publica en Kafka.
 */
public record EventoClinico(
        UUID eventId,
        TipoEvento tipo,
        UUID pacienteId,
        NivelTriaje nivel,
        EstadoPaciente estadoAnterior,   // null en el registro
        EstadoPaciente estadoNuevo,
        Instant ocurridoEn,
        String correlationId) {

    public static EventoClinico registro(Paciente paciente, String correlationId) {
        return new EventoClinico(UUID.randomUUID(), TipoEvento.PACIENTE_REGISTRADO, paciente.id(),
                paciente.nivel(), null, paciente.estado(), paciente.fechaRegistro(), correlationId);
    }

    /** RF-02: evento de una transición válida (entrada a consulta o alta). */
    public static EventoClinico cambioEstado(Paciente anterior, Paciente nuevo, String correlationId) {
        return new EventoClinico(UUID.randomUUID(), tipoParaEstado(nuevo.estado()), nuevo.id(),
                nuevo.nivel(), anterior.estado(), nuevo.estado(), nuevo.fechaEstado(), correlationId);
    }

    /**
     * RF-03: el paciente sigue en REGISTRADO; cambia el flag de escalamiento. Por eso el estado anterior
     * y el nuevo coinciden, y la auditoría registra el tipo de evento.
     */
    public static EventoClinico escalamiento(Escalamiento e, String correlationId) {
        return new EventoClinico(UUID.randomUUID(), TipoEvento.PACIENTE_ESCALADO, e.pacienteId(), e.nivel(),
                EstadoPaciente.REGISTRADO, EstadoPaciente.REGISTRADO, e.fechaEscalado(), correlationId);
    }

    private static TipoEvento tipoParaEstado(EstadoPaciente estado) {
        return switch (estado) {
            case EN_CONSULTA -> TipoEvento.PACIENTE_EN_CONSULTA;
            case ALTA -> TipoEvento.PACIENTE_ALTA;
            case REGISTRADO -> throw new IllegalArgumentException(
                    "REGISTRADO no es un cambio de estado: se emite en el registro");
        };
    }
}

