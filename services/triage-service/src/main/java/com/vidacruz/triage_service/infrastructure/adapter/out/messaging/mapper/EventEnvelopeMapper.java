package com.vidacruz.triage_service.infrastructure.adapter.out.messaging.mapper;

import com.vidacruz.events_contract.EventEnvelope;
import com.vidacruz.events_contract.EventType;
import com.vidacruz.events_contract.PacienteEventPayload;
import com.vidacruz.triage_service.domain.model.EventoClinico;

/** Traduce el hecho clínico del dominio al envelope público de {@code triaje.eventos}. */
public final class EventEnvelopeMapper {

    private EventEnvelopeMapper() {
    }

    public static EventEnvelope toEnvelope(EventoClinico evento) {
        PacienteEventPayload payload = new PacienteEventPayload(
                evento.pacienteId(),
                evento.nivel().codigo(),
                evento.estadoAnterior() == null ? null : evento.estadoAnterior().name(),
                evento.estadoNuevo().name(),
                evento.slaMinutos(),
                evento.fechaRegistro());

        return new EventEnvelope(
                evento.eventId(),
                EventType.valueOf(evento.tipo().name()),
                EventEnvelope.CURRENT_VERSION,
                evento.ocurridoEn(),
                evento.correlationId(),
                payload);
    }
}
