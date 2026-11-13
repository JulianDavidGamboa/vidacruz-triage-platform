package com.vidacruz.triage_service.integration;

import org.junit.jupiter.api.Test;

class OpenApiIT extends IntegrationTestBase {

    @Test
    void documentacionOpenApiListaLosEndpointsConLaApiKey() {
        web.get().uri("/v3/api-docs")
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").exists()
                .jsonPath("$.paths['/triage/pacientes/{id}']").exists()
                .jsonPath("$.paths['/triage/reportes/operacion']").exists();
    }

    @Test
    void documentacionOpenApiRequiereApiKey() {
        web.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
