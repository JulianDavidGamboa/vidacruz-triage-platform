package com.vidacruz.triage_service.integration;

import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Kafka KRaft de un solo nodo, inspirado en {@code deploy/docker/docker-compose.yml} pero con listeners de un solo
 * nombre para el host de las pruebas.
 *
 * <p>No se usa {@code org.testcontainers.kafka.KafkaContainer}: en Testcontainers 1.21.4 con la imagen
 * {@code apache/kafka:3.9.0} sobre Podman, esa clase genera {@code advertised.listeners} con {@code 0.0.0.0} y el
 * broker no arranca. Aquí los listeners son explícitos. El puerto del host es fijo porque el
 * {@code advertised.listeners} debe conocerse antes de que arranque el contenedor.
 */
final class KafkaKraftContainer {

    static final int PUERTO_HOST = 39092;

    private static final String IMAGEN = "apache/kafka:3.9.0";

    private KafkaKraftContainer() {
    }

    static FixedHostPortGenericContainer<?> crear() {
        FixedHostPortGenericContainer<?> kafka = new FixedHostPortGenericContainer<>(IMAGEN)
                .withFixedExposedPort(PUERTO_HOST, 9092)
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,CONTROLLER://:9093")
                .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:" + PUERTO_HOST)
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));
        Runtime.getRuntime().addShutdownHook(new Thread(kafka::stop));
        return kafka;
    }

    static String bootstrapServers() {
        return "localhost:" + PUERTO_HOST;
    }
}
