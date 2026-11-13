package com.vidacruz.triage_service.infrastructure.adapter.out.messaging;

import com.vidacruz.events_contract.EventEnvelope;
import com.vidacruz.triage_service.application.port.out.OutboxPort;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.infrastructure.adapter.out.messaging.mapper.EventEnvelopeMapper;
import com.vidacruz.triage_service.infrastructure.adapter.out.messaging.mapper.OutboxEventMapper;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class OutboxR2dbcAdapter implements OutboxPort {

    private static final String ELIMINAR_PUBLICADOS = """
            DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < :limite
            """;

    private final R2dbcEntityTemplate template;
    private final DatabaseClient db;
    private final ObjectMapper objectMapper;

    public OutboxR2dbcAdapter(R2dbcEntityTemplate template, DatabaseClient db, ObjectMapper objectMapper) {
        this.template = template;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> guardar(EventoClinico evento) {
        EventEnvelope envelope = EventEnvelopeMapper.toEnvelope(evento);
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(envelope))
                .map(json -> OutboxEventMapper.toEntity(envelope, json))
                .flatMap(entity -> template.insert(entity))
                .then();
    }

    @Override
    public Mono<Long> eliminarPublicadosAntesDe(Instant limite) {
        return db.sql(ELIMINAR_PUBLICADOS)
                .bind("limite", OffsetDateTime.ofInstant(limite, ZoneOffset.UTC))
                .fetch().rowsUpdated();
    }
}

