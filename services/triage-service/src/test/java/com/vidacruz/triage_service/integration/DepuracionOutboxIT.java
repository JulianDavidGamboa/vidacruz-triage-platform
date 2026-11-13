package com.vidacruz.triage_service.integration;

import com.vidacruz.triage_service.application.port.out.OutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "triage.outbox.poll-interval=1h",
        "triage.outbox.purge-enabled=false",
        "triage.sla.monitor-enabled=false"})
class DepuracionOutboxIT extends IntegrationTestBase {

    @Autowired OutboxPort outbox;

    private Instant ahora;

    @BeforeEach
    void limpiar() {
        db.sql("TRUNCATE TABLE outbox_events").then().block();
        ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private UUID insertar(Instant creado, Instant publicado) {
        UUID id = UUID.randomUUID();
        var spec = db.sql("""
                INSERT INTO outbox_events (id, aggregate_id, event_type, payload, created_at, published_at)
                VALUES (:id, :agregado, 'paciente_registrado', '{}'::jsonb, :creado, :publicado)
                """)
                .bind("id", id)
                .bind("agregado", UUID.randomUUID())
                .bind("creado", OffsetDateTime.ofInstant(creado, ZoneOffset.UTC));
        spec = publicado == null
                ? spec.bindNull("publicado", OffsetDateTime.class)
                : spec.bind("publicado", OffsetDateTime.ofInstant(publicado, ZoneOffset.UTC));
        spec.then().block();
        return id;
    }

    private boolean existe(UUID id) {
        return contar("SELECT count(*) AS n FROM outbox_events WHERE id = :id", id) == 1L;
    }

    @Test
    void borraSoloLosPublicadosAnterioresAlLimite() {
        Instant limite = ahora.minus(7, ChronoUnit.DAYS);
        UUID publicadoViejo = insertar(limite.minus(2, ChronoUnit.DAYS), limite.minusSeconds(1));
        UUID publicadoReciente = insertar(limite.minus(2, ChronoUnit.DAYS), limite.plusSeconds(1));
        UUID publicadoEnElLimite = insertar(limite.minus(2, ChronoUnit.DAYS), limite);
        UUID pendienteViejo = insertar(limite.minus(30, ChronoUnit.DAYS), null);

        Long borrados = outbox.eliminarPublicadosAntesDe(limite).block();

        assertThat(borrados).isEqualTo(1L);
        assertThat(existe(publicadoViejo)).isFalse();
        assertThat(existe(publicadoReciente)).isTrue();
        assertThat(existe(publicadoEnElLimite)).isTrue();
        assertThat(existe(pendienteViejo)).isTrue();
    }

    @Test
    void sinFilasElegiblesDevuelveCero() {
        insertar(ahora.minus(30, ChronoUnit.DAYS), null);

        assertThat(outbox.eliminarPublicadosAntesDe(ahora).block()).isZero();
    }
}
