package com.vidacruz.triage_service.integration;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * RF-05 contra PostgreSQL real. Usa una ventana de 2031 para no chocar con otras pruebas de la misma base.
 * Hora 10:00 → 3 registros (esperas 10, 30 y sin consulta). Hora 11:00 → 1 registro (espera 2).
 * El registro de las 12:00 cae justo en {@code hasta}, que es exclusivo, y no debe aparecer.
 */
class ReporteOperacionIT extends IntegrationTestBase {

    private static final String DESDE = "2031-01-01T10:00:00Z";
    private static final String HASTA = "2031-01-01T12:00:00Z";

    @Test
    void agrupaPorHoraDeRegistroConConteosPorNivelYTiemposDeEspera() {
        insertar("2031-01-01T10:05:00Z", 1, "2031-01-01T10:15:00Z");
        insertar("2031-01-01T10:20:00Z", 2, "2031-01-01T10:50:00Z");
        insertar("2031-01-01T10:30:00Z", 5, null);
        insertar("2031-01-01T11:10:00Z", 1, "2031-01-01T11:12:00Z");
        insertar("2031-01-01T12:00:00Z", 1, "2031-01-01T12:01:00Z");

        web.get().uri(uri -> uri.path("/triage/reportes/operacion")
                        .queryParam("desde", DESDE).queryParam("hasta", HASTA).build())
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.franjas.length()").isEqualTo(2)
                .jsonPath("$.franjas[0].hora").isEqualTo("2031-01-01T10:00:00Z")
                .jsonPath("$.franjas[0].totalRegistrados").isEqualTo(3)
                .jsonPath("$.franjas[0].porNivel.CRITICO").isEqualTo(1)
                .jsonPath("$.franjas[0].porNivel.URGENTE").isEqualTo(1)
                .jsonPath("$.franjas[0].porNivel.NO_URGENTE_2").isEqualTo(1)
                .jsonPath("$.franjas[0].rankingVolumen").isEqualTo(1)
                .jsonPath("$.franjas[0].tiempoPromedioEsperaMin").value(v ->
                        assertThat((Double) v).isCloseTo(20.0, within(0.001)))
                // percentile_cont(0.95) sobre [10, 30] = 10 + 0.95 * (30 - 10)
                .jsonPath("$.franjas[0].p95EsperaMin").value(v ->
                        assertThat((Double) v).isCloseTo(29.0, within(0.001)))
                .jsonPath("$.franjas[1].hora").isEqualTo("2031-01-01T11:00:00Z")
                .jsonPath("$.franjas[1].totalRegistrados").isEqualTo(1)
                .jsonPath("$.franjas[1].rankingVolumen").isEqualTo(2)
                .jsonPath("$.franjas[1].tiempoPromedioEsperaMin").value(v ->
                        assertThat((Double) v).isCloseTo(2.0, within(0.001)));
    }

    @Test
    void rangoInvertidoDevuelve400() {
        web.get().uri(uri -> uri.path("/triage/reportes/operacion")
                        .queryParam("desde", HASTA).queryParam("hasta", DESDE).build())
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void faltaUnParametroDevuelve400() {
        web.get().uri(uri -> uri.path("/triage/reportes/operacion")
                        .queryParam("desde", DESDE).build())
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private void insertar(String registro, int nivel, String primeraConsulta) {
        OffsetDateTime fechaRegistro = OffsetDateTime.parse(registro).withOffsetSameInstant(ZoneOffset.UTC);
        boolean enConsulta = primeraConsulta != null;
        var spec = db.sql("""
                        INSERT INTO pacientes (id, nombre, edad, fc, p_sistolica, p_diastolica, spo2, temperatura,
                                               dolor, gcs, queja_principal, nivel, sla_minutos, estado, escalado,
                                               fecha_registro, fecha_estado, fecha_primera_consulta)
                        VALUES (:id, 'Paciente de prueba', 40, 80, 120, 80, 98, 36.5,
                                2, 15, 'Prueba', :nivel, 60, :estado, FALSE,
                                :registro, :registro, :primera)
                        """)
                .bind("id", UUID.randomUUID())
                .bind("nivel", nivel)
                .bind("estado", enConsulta ? "EN_CONSULTA" : "REGISTRADO")
                .bind("registro", fechaRegistro);
        spec = enConsulta
                ? spec.bind("primera", OffsetDateTime.parse(primeraConsulta).withOffsetSameInstant(ZoneOffset.UTC))
                : spec.bindNull("primera", OffsetDateTime.class);
        spec.then().block();
    }
}
