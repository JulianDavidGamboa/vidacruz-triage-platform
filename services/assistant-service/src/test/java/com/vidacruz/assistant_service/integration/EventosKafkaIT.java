package com.vidacruz.assistant_service.integration;

import com.vidacruz.events_contract.Topics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "assistant.api-key=test-key",
        "assistant.mensajeria.enabled=true",
        "spring.ai.openai.api-key=sk-test-no-usar"
})
@AutoConfigureWebTestClient
class EventosKafkaIT {

    private static final String API_KEY = "test-key";
    private static final Duration ESPERA = Duration.ofSeconds(30);

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    static final FixedHostPortGenericContainer<?> KAFKA = KafkaKraftContainer.crear();

    static {
        REDIS.start();
        KAFKA.start();
    }

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("assistant.kafka.bootstrap-servers", KafkaKraftContainer::bootstrapServers);
    }

    @Test
    void registroYAltaActualizanLaVistaDeUrgencias() throws Exception {
        long antes = total();
        UUID paciente = UUID.randomUUID();

        publicar(evento("paciente_registrado", paciente, 2, "REGISTRADO"));
        esperarTotal(antes + 1);

        publicar(evento("paciente_alta", paciente, 2, "ALTA"));
        esperarTotal(antes);
    }

    @Test
    void escaladoNoCambiaLaVistaDeUrgencias() throws Exception {
        long antes = total();
        UUID paciente = UUID.randomUUID();

        publicar(evento("paciente_escalado", paciente, 1, "REGISTRADO"));
        publicar(evento("paciente_registrado", UUID.randomUUID(), 1, "REGISTRADO"));

        esperarTotal(antes + 1);
    }

    private long total() {
        Map<?, ?> cuerpo = client.get().uri("/asistente/estado-urgencias")
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return ((Number) cuerpo.get("total")).longValue();
    }

    private void esperarTotal(long esperado) throws InterruptedException {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < limite) {
            if (total() == esperado) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(total()).as("total de urgencias tras consumir Kafka").isEqualTo(esperado);
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

    private static String evento(String tipo, UUID paciente, int nivel, String estadoNuevo) {
        Instant ahora = Instant.now();
        return """
                {"eventId":"%s","type":"%s","version":2,"occurredAt":"%s","correlationId":"it",
                 "payload":{"pacienteId":"%s","nivel":%d,"estadoAnterior":null,"estadoNuevo":"%s",
                            "slaMinutos":30,"fechaRegistro":"%s"}}
                """.formatted(UUID.randomUUID(), tipo, ahora, paciente, nivel, estadoNuevo, ahora);
    }
}
