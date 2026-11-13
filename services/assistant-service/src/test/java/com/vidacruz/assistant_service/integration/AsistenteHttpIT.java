package com.vidacruz.assistant_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;

class AsistenteHttpIT extends IntegrationTestBase {

    private static final String CONSULTA = "/asistente/consulta";

    @Autowired
    private WebTestClient client;

    @Test
    void consultaSinApiKeyEsNoAutorizada() {
        client.post().uri(CONSULTA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", "presión 190/110"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void sinCorpusCargadoLaConsultaRespondeModoDegradado() {
        client.post().uri(CONSULTA)
                .header("X-API-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", "presión 190/110"))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.detail").value(containsString("modo degradado"));
    }

    @Test
    void preguntaVaciaEsBadRequest() {
        client.post().uri(CONSULTA)
                .header("X-API-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", "  "))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void documentacionOpenApiListaLosEndpointsConLaApiKey() {
        client.get().uri("/v3/api-docs")
                .header("X-API-Key", "test-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/asistente/consulta']").exists()
                .jsonPath("$.paths['/asistente/estado-urgencias']").exists();
    }

    @Test
    void healthEsPublico() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().is2xxSuccessful();
    }
}
