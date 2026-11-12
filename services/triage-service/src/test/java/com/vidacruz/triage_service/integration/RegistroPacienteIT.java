package com.vidacruz.triage_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Flujo completo de RF-01 contra PostgreSQL y Kafka reales (requiere Docker). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistroPacienteIT {

    private static final String API_KEY = "test-api-key";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String host = postgres.getHost();
        int port = postgres.getMappedPort(5432);
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(host, port, postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("triage.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("triage.api-key", () -> API_KEY);
    }

    @Autowired WebTestClient web;
    @Autowired DatabaseClient db;

    private static Map<String, Object> pacienteE1() {
        return Map.of(
                "nombreCompleto", "Carlos Gómez",
                "edad", 55,
                "frecuenciaCardiaca", 130,
                "presionSistolica", 180,
                "presionDiastolica", 110,
                "spo2", 97,
                "temperatura", 36.8,
                "dolor", 8,
                "gcs", 15,
                "quejaPrincipal", "Dolor torácico");
    }

    @Test
    void registraPacienteCriticoConAuditoriaYEventoPublicado() {
        web.post().uri("/triage/pacientes")
                .header("X-API-Key", API_KEY)
                .header("X-Correlation-Id", "it-corr-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pacienteE1())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-Correlation-Id", "it-corr-1")
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.nivel").isEqualTo("CRITICO")
                .jsonPath("$.nivelCodigo").isEqualTo(1)
                .jsonPath("$.slaMinutos").isEqualTo(5)
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.fechaRegistro").isNotEmpty();

        // Auditoría con el correlationId recibido
        Long auditorias = db.sql("SELECT count(*) AS n FROM auditoria_clinica WHERE correlation_id = 'it-corr-1' "
                        + "AND tipo_evento = 'PACIENTE_REGISTRADO' AND estado_anterior IS NULL AND estado_nuevo = 'REGISTRADO'")
                .map(row -> row.get("n", Long.class)).one().block();
        assertThat(auditorias).isEqualTo(1L);

        // El relay publica el evento en Kafka y lo marca como publicado
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Long pendientes = db.sql("SELECT count(*) AS n FROM outbox_events "
                            + "WHERE event_type = 'paciente_registrado' AND published_at IS NULL")
                    .map(row -> row.get("n", Long.class)).one().block();
            Long publicados = db.sql("SELECT count(*) AS n FROM outbox_events "
                            + "WHERE event_type = 'paciente_registrado' AND published_at IS NOT NULL")
                    .map(row -> row.get("n", Long.class)).one().block();
            assertThat(pendientes).isZero();
            assertThat(publicados).isGreaterThanOrEqualTo(1L);
        });
    }

    @Test
    void valorFueraDeRangoDevuelve400IndicandoElCampo() {
        Map<String, Object> invalido = new java.util.HashMap<>(pacienteE1());
        invalido.put("frecuenciaCardiaca", 300);

        web.post().uri("/triage/pacientes")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalido)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errores[0].campo").isEqualTo("frecuenciaCardiaca")
                .jsonPath("$.detail").value(d -> assertThat(d.toString()).contains("frecuenciaCardiaca"));
    }

    @Test
    void diastolicaMayorQueSistolicaDevuelve400() {
        Map<String, Object> invalido = new java.util.HashMap<>(pacienteE1());
        invalido.put("presionSistolica", 100);
        invalido.put("presionDiastolica", 110);

        web.post().uri("/triage/pacientes")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalido)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").value(d -> assertThat(d.toString()).contains("presionDiastolica"));
    }

    @Test
    void jsonMalFormadoDevuelve400() {
        web.post().uri("/triage/pacientes")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{ esto no es json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void sinApiKeyDevuelve401() {
        web.post().uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pacienteE1())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void healthEsPublico() {
        web.get().uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void cadaRegistroGeneraUnIdDistinto() {
        UUID a = registrarYObtenerId();
        UUID b = registrarYObtenerId();
        assertThat(a).isNotEqualTo(b);
    }

    private UUID registrarYObtenerId() {
        String id = web.post().uri("/triage/pacientes")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pacienteE1())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody().get("id").toString();
        return UUID.fromString(id);
    }
}
