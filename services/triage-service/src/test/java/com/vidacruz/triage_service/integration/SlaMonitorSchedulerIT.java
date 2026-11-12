package com.vidacruz.triage_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * El scheduler real, con intervalo de 1 s. Para no esperar un SLA de verdad se retrasa el registro en la
 * base de datos; así se prueba el ciclo completo: scheduler → caso de uso → SQL → auditoría → Kafka.
 */
@TestPropertySource(properties = {
        "triage.sla.monitor-enabled=true",
        "triage.sla.evaluation-interval=1s"
})
class SlaMonitorSchedulerIT extends IntegrationTestBase {

    @Test
    void elSchedulerEscalaSoloALosVencidosYUnaSolaVez() {
        String vencido = registrar(pacienteE1());
        String enConsulta = registrar(pacienteE1());
        String reciente = registrar(pacienteE1());
        cambiarEstado(enConsulta, "EN_CONSULTA").expectStatus().isOk();

        // Simula que "vencido" y "enConsulta" se registraron hace una hora (SLA crítico = 5 min)
        db.sql("UPDATE pacientes SET fecha_registro = now() - interval '1 hour' WHERE id IN (:a, :b)")
                .bind("a", UUID.fromString(vencido))
                .bind("b", UUID.fromString(enConsulta))
                .fetch().rowsUpdated().block();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(escalado(vencido)).isTrue());

        // Deja correr varios ciclos más: no debe volver a escalarse ni duplicar eventos
        await().pollDelay(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(contar("SELECT count(*) AS n FROM auditoria_clinica WHERE paciente_id = :id "
                    + "AND tipo_evento = 'PACIENTE_ESCALADO'", UUID.fromString(vencido))).isEqualTo(1L);
            assertThat(contar("SELECT count(*) AS n FROM outbox_events WHERE aggregate_id = :id "
                    + "AND event_type = 'paciente_escalado'", UUID.fromString(vencido))).isEqualTo(1L);
            assertThat(contar("SELECT count(*) AS n FROM outbox_events WHERE aggregate_id = :id "
                    + "AND event_type = 'paciente_escalado' AND published_at IS NOT NULL",
                    UUID.fromString(vencido))).isEqualTo(1L);
        });

        assertThat(escalado(enConsulta)).isFalse();   // en consulta: nunca se escala
        assertThat(escalado(reciente)).isFalse();     // dentro de su SLA

        web.get().uri("/triage/pacientes/{id}", vencido)
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.flags.escalado").isEqualTo(true)
                .jsonPath("$.estado").isEqualTo("REGISTRADO");
    }
}
