package com.vidacruz.triage_service.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documenta la API y el esquema de autenticación. Así Swagger UI envía el header X-API-Key tras el botón "Authorize".
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Triage Service API")
                        .version("1.0.0")
                        .description("Registro, clasificación, seguimiento de SLA y reportes de pacientes en urgencias"))
                .components(new Components().addSecuritySchemes("apiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }
}
