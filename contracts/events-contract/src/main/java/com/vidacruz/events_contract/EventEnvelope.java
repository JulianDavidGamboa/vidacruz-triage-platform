package com.vidacruz.events_contract;

import jdk.jfr.EventType;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        EventType type,
        int version,
        Instant occurredAt,
        String correlationId,
        PacienteEventPayload payload) {

    public static final int CURRENT_VERSION = 1;
}