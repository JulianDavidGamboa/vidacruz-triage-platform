package com.vidacruz.triage_service.integration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Transiciones, 409, GET, auditoría y eventos en Kafka de extremo a extremo. */
class FlujoEstadosIT extends IntegrationTestBase {

    @Test
    void registradoAEnConsultaActualizaGetAuditaYPublica() {
        String id = registrar(pacienteE1());

        cambiarEstado(id, "EN_CONSULTA")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                .jsonPath("$.fechaPrimeraConsulta").isNotEmpty();

        web.get().uri("/triage/pacientes/{id}", id)
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                .jsonPath("$.nivel").isEqualTo("CRITICO")
                .jsonPath("$.flags.escalado").isEqualTo(false)
                .jsonPath("$.minutosEnEstadoActual").isNumber()
                .jsonPath("$.signosVitales.frecuenciaCardiaca").isEqualTo(130);

        UUID uuid = UUID.fromString(id);
        assertThat(contar("SELECT count(*) AS n FROM auditoria_clinica "
                + "WHERE paciente_id = :id AND tipo_evento = 'PACIENTE_EN_CONSULTA' AND estado_anterior = 'REGISTRADO'", uuid))
                .isEqualTo(1L);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(contar("SELECT count(*) AS n FROM outbox_events "
                        + "WHERE aggregate_id = :id AND event_type = 'paciente_en_consulta' AND published_at IS NOT NULL",
                        uuid)).isEqualTo(1L));
    }

    @Test
    void altaEsTerminalYDevuelve409ConMensajeDescriptivo() {
        String id = registrar(pacienteE1());
        cambiarEstado(id, "ALTA").expectStatus().isOk();

        cambiarEstado(id, "EN_CONSULTA")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").value(d -> assertThat(d.toString())
                        .contains("Transición inválida de ALTA a EN_CONSULTA"));

        cambiarEstado(id, "ALTA").expectStatus().isEqualTo(409);
    }

    @Test
    void enConsultaNoVuelveARegistrado() {
        String id = registrar(pacienteE1());
        cambiarEstado(id, "EN_CONSULTA").expectStatus().isOk();

        cambiarEstado(id, "REGISTRADO").expectStatus().isEqualTo(409);
    }

    @Test
    void registradoPuedeDarseDeAltaDirectamente() {
        String id = registrar(pacienteE1());

        cambiarEstado(id, "ALTA")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ALTA")
                .jsonPath("$.fechaPrimeraConsulta").doesNotExist();
    }

    @Test
    void estadoDesconocidoDevuelve400() {
        String id = registrar(pacienteE1());

        cambiarEstado(id, "CANCELADO").expectStatus().isBadRequest();
    }

    @Test
    void pacienteInexistenteDevuelve404() {
        String id = UUID.randomUUID().toString();

        cambiarEstado(id, "EN_CONSULTA").expectStatus().isNotFound();

        web.get().uri("/triage/pacientes/{id}", id)
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void idMalFormadoDevuelve400() {
        cambiarEstado("no-es-uuid", "EN_CONSULTA").expectStatus().isBadRequest();
    }

    @Test
    void sinApiKeyDevuelve401() {
        web.get().uri("/triage/pacientes/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
