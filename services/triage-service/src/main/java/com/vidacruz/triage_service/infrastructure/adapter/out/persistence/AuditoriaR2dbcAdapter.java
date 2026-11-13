package com.vidacruz.triage_service.infrastructure.adapter.out.persistence;

import com.vidacruz.triage_service.application.port.out.AuditPort;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Solo inserta. La tabla tiene un trigger que rechaza UPDATE y DELETE. */
@Component
public class AuditoriaR2dbcAdapter implements AuditPort {

    private static final String INSERTAR = """
            INSERT INTO auditoria_clinica (paciente_id, tipo_evento, estado_anterior, estado_nuevo, ts, correlation_id)
            VALUES (:pacienteId, :tipoEvento, :estadoAnterior, :estadoNuevo, :ts, :correlationId)
            """;

    private final DatabaseClient db;

    public AuditoriaR2dbcAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Void> registrar(EventoClinico evento) {
        DatabaseClient.GenericExecuteSpec spec = db.sql(INSERTAR)
                .bind("pacienteId", evento.pacienteId())
                .bind("tipoEvento", evento.tipo().name())
                .bind("estadoNuevo", evento.estadoNuevo().name())
                .bind("ts", OffsetDateTime.ofInstant(evento.ocurridoEn(), ZoneOffset.UTC))
                .bind("correlationId", evento.correlationId());
        spec = evento.estadoAnterior() == null
                ? spec.bindNull("estadoAnterior", String.class)
                : spec.bind("estadoAnterior", evento.estadoAnterior().name());
        return spec.then();
    }
}
