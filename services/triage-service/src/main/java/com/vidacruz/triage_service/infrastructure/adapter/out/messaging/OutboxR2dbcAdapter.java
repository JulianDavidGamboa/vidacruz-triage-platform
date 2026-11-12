package com.vidacruz.triage_service.infrastructure.adapter.out.messaging;

import com.vidacruz.events_contract.EventEnvelope;
import com.vidacruz.triage_service.application.port.out.OutboxPort;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import com.vidacruz.triage_service.infrastructure.adapter.out.messaging.mapper.EventEnvelopeMapper;
import com.vidacruz.triage_service.infrastructure.adapter.out.messaging.mapper.OutboxEventMapper;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OutboxR2dbcAdapter implements OutboxPort {

    private final R2dbcEntityTemplate template;
    private final ObjectMapper objectMapper;

    public OutboxR2dbcAdapter(R2dbcEntityTemplate template, ObjectMapper objectMapper) {
        this.template = template;
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
}

