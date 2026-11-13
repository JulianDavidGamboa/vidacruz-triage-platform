package com.vidacruz.triage_service.infrastructure.adapter.out.persistence;

import com.vidacruz.triage_service.application.port.in.FranjaHorariaVista;
import com.vidacruz.triage_service.application.port.out.ReporteOperacionPort;
import com.vidacruz.triage_service.domain.model.NivelTriaje;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

/**
 * Toda la agregación ocurre en PostgreSQL: CTE para calcular la espera por paciente, {@code FILTER}
 * para el conteo por nivel, {@code percentile_cont} para el p95 y {@code RANK() OVER} para el ranking de
 * volumen por hora. Los pacientes sin consulta aportan a los totales pero no a los tiempos de espera.
 */
@Component
public class ReporteOperacionR2dbcAdapter implements ReporteOperacionPort {

    private static final String RESUMEN_POR_HORA = """
            WITH base AS (
                SELECT date_trunc('hour', fecha_registro AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS hora,
                       nivel,
                       EXTRACT(EPOCH FROM (fecha_primera_consulta - fecha_registro))::double precision / 60.0
                           AS espera_min
                FROM pacientes
                WHERE fecha_registro >= :desde AND fecha_registro < :hasta
            ),
            por_hora AS (
                SELECT hora,
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE nivel = 1) AS nivel_1,
                       COUNT(*) FILTER (WHERE nivel = 2) AS nivel_2,
                       COUNT(*) FILTER (WHERE nivel = 3) AS nivel_3,
                       COUNT(*) FILTER (WHERE nivel = 4) AS nivel_4,
                       COUNT(*) FILTER (WHERE nivel = 5) AS nivel_5,
                       AVG(espera_min) AS promedio_espera,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY espera_min) AS p95_espera
                FROM base
                GROUP BY hora
            )
            SELECT hora, total, nivel_1, nivel_2, nivel_3, nivel_4, nivel_5,
                   promedio_espera, p95_espera,
                   RANK() OVER (ORDER BY total DESC) AS ranking_volumen
            FROM por_hora
            ORDER BY hora
            """;

    private final DatabaseClient db;

    public ReporteOperacionR2dbcAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Flux<FranjaHorariaVista> resumirPorHora(Instant desde, Instant hasta) {
        return db.sql(RESUMEN_POR_HORA)
                .bind("desde", OffsetDateTime.ofInstant(desde, ZoneOffset.UTC))
                .bind("hasta", OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC))
                .map((row, metadata) -> aFranja(row))
                .all();
    }

    private static FranjaHorariaVista aFranja(Row row) {
        Map<NivelTriaje, Long> porNivel = new EnumMap<>(NivelTriaje.class);
        for (NivelTriaje nivel : NivelTriaje.values()) {
            porNivel.put(nivel, row.get("nivel_" + nivel.codigo(), Long.class));
        }
        return new FranjaHorariaVista(
                row.get("hora", OffsetDateTime.class).toInstant(),
                row.get("total", Long.class),
                porNivel,
                row.get("promedio_espera", Double.class),
                row.get("p95_espera", Double.class),
                row.get("ranking_volumen", Long.class));
    }
}
