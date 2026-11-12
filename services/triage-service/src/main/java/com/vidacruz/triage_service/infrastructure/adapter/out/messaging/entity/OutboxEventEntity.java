package com.vidacruz.triage_service.infrastructure.adapter.out.messaging.entity;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de outbox_events. La columna seq (orden de publicación) la asigna la BD. */
@Table("outbox_events")
public record OutboxEventEntity(
        @Id UUID id,
        @Column("aggregate_id") UUID aggregateId,
        @Column("event_type") String eventType,
        @Column("payload") Json payload,
        @Column("created_at") Instant createdAt,
        @Column("published_at") Instant publishedAt) {
}

