package com.vidacruz.triage_service.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL y Kafka reales compartidos por todas las pruebas de integración (requiere Docker o Podman).
 *
 * <p>Los contenedores son singleton de la JVM y no se detienen entre clases: Spring cachea el contexto entre
 * clases y su pool de conexiones R2DBC apunta al host y puerto del primer contenedor. Si cada clase levantara
 * el suyo, una clase posterior podría reutilizar un contexto cuyo Postgres ya está detenido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    protected static final String API_KEY = "test-api-key";

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final FixedHostPortGenericContainer<?> kafka = KafkaKraftContainer.crear();

    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String host = postgres.getHost();
        int port = postgres.getMappedPort(5432);
        registry.add("spring.r2dbc.url",
                () -> "r2dbc:postgresql://%s:%d/%s".formatted(host, port, postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("triage.kafka.bootstrap-servers", KafkaKraftContainer::bootstrapServers);
        registry.add("triage.api-key", () -> API_KEY);
    }

    @Autowired protected WebTestClient web;
    @Autowired protected DatabaseClient db;

    /** E1 del enunciado → CRITICO, SLA 5 min. */
    protected static Map<String, Object> pacienteE1() {
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

    /** E5 del enunciado → NO_URGENTE_2, SLA 120 min. */
    protected static Map<String, Object> pacienteE5() {
        return Map.of(
                "nombreCompleto", "Laura Díaz",
                "edad", 30,
                "frecuenciaCardiaca", 70,
                "presionSistolica", 115,
                "presionDiastolica", 75,
                "spo2", 99,
                "temperatura", 36.4,
                "dolor", 0,
                "gcs", 15,
                "quejaPrincipal", "Renovación de recetario");
    }

    protected String registrar(Map<String, Object> paciente) {
        return web.post().uri("/triage/pacientes")
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paciente)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody()
                .get("id").toString();
    }

    protected WebTestClient.ResponseSpec cambiarEstado(String id, String estado) {
        return web.patch().uri("/triage/pacientes/{id}/estado", id)
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("estado", estado))
                .exchange();
    }

    /** Ejecuta un SELECT count(*) AS n ... WHERE ... :id ... */
    protected long contar(String sql, UUID pacienteId) {
        return db.sql(sql).bind("id", pacienteId)
                .map(row -> row.get("n", Long.class)).one().block();
    }

    protected boolean escalado(String pacienteId) {
        return Boolean.TRUE.equals(db.sql("SELECT escalado FROM pacientes WHERE id = :id")
                .bind("id", UUID.fromString(pacienteId))
                .map(row -> row.get("escalado", Boolean.class)).one().block());
    }
}
