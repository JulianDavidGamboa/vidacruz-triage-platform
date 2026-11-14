package com.vidacruz.alert_service.infrastructure.adapter.out.canal;

import com.vidacruz.alert_service.domain.model.Alerta;
import com.vidacruz.alert_service.domain.model.NivelTriaje;
import com.vidacruz.alert_service.infrastructure.config.AlertProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CanalDiscordTest {

    private final UUID pacienteId = UUID.randomUUID();
    private MockWebServer servidor;

    @BeforeEach
    void arrancarServidor() throws IOException {
        servidor = new MockWebServer();
        servidor.start();
    }

    @AfterEach
    void pararServidor() throws IOException {
        servidor.shutdown();
    }

    @Test
    void entregaLaAlertaConLaClaveContentYAceptaSinContenido() throws InterruptedException {
        servidor.enqueue(new MockResponse().setResponseCode(204));

        StepVerifier.create(canal(servidor.url("/hook").toString()).enviar(alerta()))
                .verifyComplete();

        RecordedRequest peticion = servidor.takeRequest();
        assertThat(peticion.getMethod()).isEqualTo("POST");
        assertThat(peticion.getBody().readUtf8())
                .startsWith("{\"content\":\"")
                .contains(pacienteId.toString())
                .contains("SLA 30 min, excedido 42 min");
    }

    @Test
    void tresFallosSeguidosPropaganElError() {
        for (int intento = 0; intento < 3; intento++) {
            servidor.enqueue(new MockResponse().setResponseCode(500));
        }

        StepVerifier.create(canal(servidor.url("/hook").toString()).enviar(alerta()))
                .verifyError();

        assertThat(servidor.getRequestCount()).isEqualTo(3);
    }

    @Test
    void sinWebhookNoEstaConfigurado() {
        assertThat(canal("").configurado()).isFalse();
        assertThat(canal("http://localhost/hook").configurado()).isTrue();
    }

    @Test
    void recortaElTextoAlLimiteDeDiscord() {
        assertThat(CanalDiscord.recortar("a".repeat(2500))).hasSize(2000);
        assertThat(CanalDiscord.recortar("corto")).isEqualTo("corto");
    }

    private CanalDiscord canal(String webhookUrl) {
        return new CanalDiscord(
                WebClient.builder(),
                new AlertProperties("", webhookUrl, 10, new AlertProperties.Kafka("localhost:9092")),
                CircuitBreaker.ofDefaults("discord-test"));
    }

    private Alerta alerta() {
        return new Alerta(pacienteId, NivelTriaje.URGENTE, Instant.parse("2026-11-12T10:00:00Z"), 30, 42L);
    }
}
