package com.vidacruz.triage_service.infrastructure.adapter.out.messaging.mapper;

import com.vidacruz.events_contract.EventEnvelope;
import com.vidacruz.triage_service.infrastructure.adapter.out.messaging.entity.OutboxEventEntity;
import io.r2dbc.postgresql.codec.Json;

/** Fila de {@code outbox_events} a partir del envelope ya serializado. */
public final class OutboxEventMapper {

    private OutboxEventMapper() {
    }

    public static OutboxEventEntity toEntity(EventEnvelope envelope, String json) {
        return new OutboxEventEntity(
                envelope.eventId(),
                envelope.payload().pacienteId(),
                envelope.type().wireName(),
                Json.of(json),
                envelope.occurredAt(),
                null);
    }
}
