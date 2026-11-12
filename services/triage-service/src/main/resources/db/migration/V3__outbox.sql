-- Transactional Outbox: los eventos se escriben aquí en la misma transacción que el cambio de estado.
CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY,
    seq           BIGSERIAL    NOT NULL UNIQUE,      -- orden de publicación
    aggregate_id  UUID         NOT NULL,             -- pacienteId → clave de partición Kafka
    event_type    VARCHAR(40)  NOT NULL,
    payload       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pendientes ON outbox_events (seq) WHERE published_at IS NULL;
