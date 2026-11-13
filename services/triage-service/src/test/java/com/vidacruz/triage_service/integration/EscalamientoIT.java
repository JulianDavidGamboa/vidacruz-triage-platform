package com.vidacruz.triage_service.integration;

import com.vidacruz.triage_service.application.port.in.EscalarPacientesVencidosUseCase;
import com.vidacruz.triage_service.application.port.out.ClockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Contra PostgreSQL y Kafka reales. El scheduler se apaga y el caso de uso se invoca
 * directamente con un reloj controlable: se prueba el límite exacto del SLA sin esperar minutos.
 */
@TestPropertySource(properties = "triage.sla.monitor-enabled=false")
class EscalamientoIT extends IntegrationTestBase {

    static class RelojMutable implements ClockPort {
        private volatile Instant ahora;

        RelojMutable(Instant inicial) {
            this.ahora = inicial;
        }

        @Override
        public Instant ahora() {
            return ahora;
        }

        void fijar(Instant instante) {
            this.ahora = instante;
        }
    }

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojMutable relojMutable() {
            return new RelojMutable(Instant.now());
        }
    }

    @Autowired EscalarPacientesVencidosUseCase escalar;
    @Autowired RelojMutable reloj;

    private Instant t0;

    @BeforeEach
    void limpiarYFijarReloj() {
        // Una sola sentencia: auditoria_clinica referencia a pacientes. TRUNCATE no dispara el trigger de inmutabilidad.
        db.sql("TRUNCATE TABLE auditoria_clinica, outbox_events, pacientes").then().block();
        t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);   // anclado al reloj real: otros contextos no ven vencidos
        reloj.fijar(t0);
    }

    private void avanzar(Duration duracion) {
        reloj.fijar(t0.plus(duracion));
    }

    private long auditoriasDeEscalamiento(String id) {
        return contar("SELECT count(*) AS n FROM auditoria_clinica WHERE paciente_id = :id "
                + "AND tipo_evento = 'PACIENTE_ESCALADO' AND estado_anterior = 'REGISTRADO' "
                + "AND estado_nuevo = 'REGISTRADO'", UUID.fromString(id));
    }

    private long eventosDeEscalamiento(String id) {
        return contar("SELECT count(*) AS n FROM outbox_events WHERE aggregate_id = :id "
                + "AND event_type = 'paciente_escalado'", UUID.fromString(id));
    }

    @Test
    void enElLimiteExactoDelSlaNoEscalaYUnSegundoDespuesSi() {
        String id = registrar(pacienteE1());   // CRITICO → 5 min

        avanzar(Duration.ofMinutes(5));
        escalar.ejecutar().block();
        assertThat(escalado(id)).isFalse();

        avanzar(Duration.ofMinutes(5).plusSeconds(1));
        Long escalados = escalar.ejecutar().block();

        assertThat(escalados).isEqualTo(1L);
        assertThat(escalado(id)).isTrue();
    }

    @Test
    void escalarEsIdempotenteNoDuplicaEventoNiAuditoria() {
        String id = registrar(pacienteE1());
        avanzar(Duration.ofMinutes(10));

        assertThat(escalar.ejecutar().block()).isEqualTo(1L);
        assertThat(escalar.ejecutar().block()).isZero();
        assertThat(escalar.ejecutar().block()).isZero();

        assertThat(auditoriasDeEscalamiento(id)).isEqualTo(1L);
        assertThat(eventosDeEscalamiento(id)).isEqualTo(1L);
    }

    @Test
    void unPacienteEnConsultaONuncaSeEscala() {
        String enConsulta = registrar(pacienteE1());
        String conAlta = registrar(pacienteE1());
        cambiarEstado(enConsulta, "EN_CONSULTA").expectStatus().isOk();
        cambiarEstado(conAlta, "ALTA").expectStatus().isOk();

        avanzar(Duration.ofHours(1));

        assertThat(escalar.ejecutar().block()).isZero();
        assertThat(escalado(enConsulta)).isFalse();
        assertThat(escalado(conAlta)).isFalse();
        assertThat(auditoriasDeEscalamiento(enConsulta)).isZero();
        assertThat(eventosDeEscalamiento(conAlta)).isZero();
    }

    @Test
    void cadaNivelUsaSuPropioSla() {
        String critico = registrar(pacienteE1());     // 5 min
        String administrativo = registrar(pacienteE5()); // NO_URGENTE_2 → 120 min

        avanzar(Duration.ofMinutes(10));
        escalar.ejecutar().block();
        assertThat(escalado(critico)).isTrue();
        assertThat(escalado(administrativo)).isFalse();

        avanzar(Duration.ofMinutes(121));
        escalar.ejecutar().block();
        assertThat(escalado(administrativo)).isTrue();
    }

    @Test
    void elEventoPublicadoLlevaNivelYEstadosEsperados() {
        String id = registrar(pacienteE1());
        avanzar(Duration.ofMinutes(10));
        escalar.ejecutar().block();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(contar("SELECT count(*) AS n FROM outbox_events WHERE aggregate_id = :id "
                        + "AND event_type = 'paciente_escalado' AND published_at IS NOT NULL",
                        UUID.fromString(id))).isEqualTo(1L));

        var fila = db.sql("""
                        SELECT payload->>'type' AS tipo,
                               (payload->'payload'->>'nivel')::int AS nivel,
                               payload->'payload'->>'estadoAnterior' AS anterior,
                               payload->'payload'->>'estadoNuevo' AS nuevo
                        FROM outbox_events WHERE aggregate_id = :id AND event_type = 'paciente_escalado'
                        """)
                .bind("id", UUID.fromString(id))
                .map(row -> new String[]{
                        row.get("tipo", String.class),
                        String.valueOf(row.get("nivel", Integer.class)),
                        row.get("anterior", String.class),
                        row.get("nuevo", String.class)})
                .one().block();

        assertThat(fila).containsExactly("paciente_escalado", "1", "REGISTRADO", "REGISTRADO");
    }

    @Test
    void unPacienteEscaladoPuedeEntrarAConsultaConservandoElFlag() {
        String id = registrar(pacienteE1());
        avanzar(Duration.ofMinutes(10));
        escalar.ejecutar().block();

        cambiarEstado(id, "EN_CONSULTA").expectStatus().isOk();

        web.get().uri("/triage/pacientes/{id}", id)
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                .jsonPath("$.flags.escalado").isEqualTo(true)
                .jsonPath("$.fechaEscalado").isNotEmpty();

        // Ya en consulta: no se vuelve a escalar
        avanzar(Duration.ofHours(2));
        assertThat(escalar.ejecutar().block()).isZero();
        assertThat(auditoriasDeEscalamiento(id)).isEqualTo(1L);
    }

    @Test
    void dosEjecucionesConcurrentesEscalanCadaPacienteUnaSolaVez() {
        int total = 5;
        String[] ids = new String[total];
        for (int i = 0; i < total; i++) {
            ids[i] = registrar(pacienteE1());
        }
        avanzar(Duration.ofMinutes(10));

        var resultados = Mono.zip(escalar.ejecutar(), escalar.ejecutar()).block();

        // FOR UPDATE SKIP LOCKED: ninguno se escala dos veces ni queda sin escalar
        assertThat(resultados.getT1() + resultados.getT2()).isEqualTo((long) total);
        for (String id : ids) {
            assertThat(escalado(id)).isTrue();
            assertThat(auditoriasDeEscalamiento(id)).isEqualTo(1L);
            assertThat(eventosDeEscalamiento(id)).isEqualTo(1L);
        }
    }
}
