package com.vidacruz.triage_service.infrastructure.adapter.out.persistence;

import com.vidacruz.triage_service.application.port.out.EscalamientoRepositoryPort;
import com.vidacruz.triage_service.domain.model.Escalamiento;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Una sola sentencia marca y devuelve los escalados. {@code FOR UPDATE SKIP LOCKED} evita que dos instancias
 * escalen al mismo paciente, y la condición del WHERE hace la operación idempotente.
 */
@Component
public class EscalamientoR2dbcAdapter implements EscalamientoRepositoryPort {

    private static final String MARCAR_VENCIDOS = """
            WITH vencidos AS (
                SELECT id FROM pacientes
                 WHERE estado = 'REGISTRADO'
                   AND escalado = FALSE
                   AND fecha_registro + make_interval(mins => sla_minutos) < :ahora
                FOR UPDATE SKIP LOCKED
            )
            UPDATE pacientes p
               SET escalado = TRUE, fecha_escalado = :ahora
              FROM vencidos v
             WHERE p.id = v.id
            RETURNING p.id, p.nivel, p.sla_minutos, p.fecha_registro, p.fecha_escalado
            """;

    private final DatabaseClient db;

    public EscalamientoR2dbcAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Flux<Escalamiento> marcarVencidosComoEscalados(Instant ahora) {
        return db.sql(MARCAR_VENCIDOS)
                .bind("ahora", OffsetDateTime.ofInstant(ahora, ZoneOffset.UTC))
                .map(row -> new Escalamiento(
                        row.get("id", UUID.class),
                        NivelTriaje.desdeCodigo(row.get("nivel", Integer.class)),
                        row.get("sla_minutos", Integer.class),
                        row.get("fecha_registro", OffsetDateTime.class).toInstant(),
                        row.get("fecha_escalado", OffsetDateTime.class).toInstant()))
                .all();
    }
}
