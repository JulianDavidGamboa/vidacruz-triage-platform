package com.vidacruz.alert_service.integration;

import com.vidacruz.events_contract.Topics;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.FixedHostPortGenericContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "alert.mensajeria.enabled=true")
class EscalamientoKafkaIT {

    static final FixedHostPortGenericContainer<?> KAFKA = KafkaKraftContainer.crear();
    static final MockWebServer TEAMS = new MockWebServer();

    static {
        KAFKA.start();
        try {
            TEAMS.start();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo arrancar el webhook falso", e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("alert.kafka.bootstrap-servers", KafkaKraftContainer::bootstrapServers);
        registry.add("alert.teams-webhook-url", () -> TEAMS.url("/hook").toString());
    }

    @Test
    void soloLosEscalamientosLlegANTeamsConLosMinutosExcedidos() throws Exception {
        TEAMS.enqueue(new MockResponse().setResponseCode(200));
        UUID escalado = UUID.randomUUID();

        publicar(evento("paciente_registrado", UUID.randomUUID(), 2, Instant.now().minus(Duration.ofMinutes(5))));
        Instant ahora = Instant.now();
        publicar(evento("paciente_escalado", escalado, 2, ahora.minus(Duration.ofMinutes(72)), ahora));

        RecordedRequest peticion = TEAMS.takeRequest(30, TimeUnit.SECONDS);

        assertThat(peticion).as("alerta recibida por el webhook").isNotNull();
        assertThat(peticion.getBody().readUtf8())
                .contains(escalado.toString())
                .contains("SLA 30 min, excedido 42 min");
    }

    private static void publicar(String json) throws Exception {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaKraftContainer.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(Topics.TRIAJE_EVENTOS, json)).get();
        }
    }

    private static String evento(String tipo, UUID paciente, int nivel, Instant registro) {
        return evento(tipo, paciente, nivel, registro, Instant.now());
    }

    private static String evento(String tipo, UUID paciente, int nivel, Instant registro, Instant ocurrido) {
        return """
                {"eventId":"%s","type":"%s","version":2,"occurredAt":"%s","correlationId":"it",
                 "payload":{"pacienteId":"%s","nivel":%d,"estadoAnterior":"REGISTRADO","estadoNuevo":"REGISTRADO",
                            "slaMinutos":30,"fechaRegistro":"%s"}}
                """.formatted(UUID.randomUUID(), tipo, ocurrido, paciente, nivel, registro);
    }
}
