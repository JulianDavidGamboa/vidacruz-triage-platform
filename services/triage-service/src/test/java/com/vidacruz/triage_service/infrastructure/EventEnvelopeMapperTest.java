package com.vidacruz.triage_service.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidacruz.events_contract.EventEnvelope;
import com.vidacruz.events_contract.EventType;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import com.vidacruz.triage_service.domain.model.Paciente;
import com.vidacruz.triage_service.domain.model.SignosVitales;
import com.vidacruz.triage_service.infrastructure.adapter.out.messaging.mapper.EventEnvelopeMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Protege el contrato público del evento: si cambia el JSON, este test falla. */
class EventEnvelopeMapperTest {

    private final JsonMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private static EventoClinico evento() {
        Paciente p = Paciente.registrar(UUID.randomUUID(), "Carlos Gómez", 55,
                new SignosVitales(130, 180, 110, 97, 36.8, 8, 15),
                "Dolor torácico", NivelTriaje.CRITICO, 5, Instant.parse("2026-10-29T14:00:00Z"));
        return EventoClinico.registro(p, "cid-42");
    }

    @Test
    void mapeaElHechoClinicoAlEnvelopeDelContrato() {
        EventoClinico e = evento();

        EventEnvelope envelope = EventEnvelopeMapper.toEnvelope(e);

        assertThat(envelope.eventId()).isEqualTo(e.eventId());
        assertThat(envelope.type()).isEqualTo(EventType.PACIENTE_REGISTRADO);
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isEqualTo(e.ocurridoEn());
        assertThat(envelope.correlationId()).isEqualTo("cid-42");
        assertThat(envelope.payload().pacienteId()).isEqualTo(e.pacienteId());
        assertThat(envelope.payload().nivel()).isEqualTo(1);
        assertThat(envelope.payload().estadoAnterior()).isNull();
        assertThat(envelope.payload().estadoNuevo()).isEqualTo("REGISTRADO");
    }

    @Test
    void elJsonUsaElNombreDeEventoDelContratoYFechaIso8601() throws Exception {
        EventEnvelope envelope = EventEnvelopeMapper.toEnvelope(evento());

        JsonNode tree = json.readTree(json.writeValueAsString(envelope));

        assertThat(tree.get("type").asText()).isEqualTo("paciente_registrado");
        assertThat(tree.get("version").asInt()).isEqualTo(1);
        assertThat(tree.get("occurredAt").asText()).isEqualTo("2026-10-29T14:00:00Z");
        assertThat(tree.get("correlationId").asText()).isEqualTo("cid-42");
        assertThat(tree.get("payload").get("nivel").asInt()).isEqualTo(1);
        assertThat(tree.get("payload").get("estadoNuevo").asText()).isEqualTo("REGISTRADO");
        assertThat(tree.get("payload").get("estadoAnterior").isNull()).isTrue();
    }

    @Test
    void unConsumidorPuedeReconstruirElEnvelopeDesdeElJson() throws Exception {
        EventEnvelope original = EventEnvelopeMapper.toEnvelope(evento());

        EventEnvelope leido = json.readValue(json.writeValueAsString(original), EventEnvelope.class);

        assertThat(leido).isEqualTo(original);
    }
}
