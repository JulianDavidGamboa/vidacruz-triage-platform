# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Maven multi-module build (Java 21, Spring Boot 3.5.16). Run everything from the repo root. There is no `mvnw` in the root yet; the wrapper lives in `services/triage-service/` and is used with `-f pom.xml`.

```bash
# Build one service and its dependencies (contracts)
./services/triage-service/mvnw -f pom.xml -pl services/triage-service -am package

# Unit tests only (surefire runs *Test / *Tests classes by default)
./services/triage-service/mvnw -f pom.xml -pl services/triage-service test

# Integration tests (*IT, Testcontainers: requires a running Docker daemon)
./services/triage-service/mvnw -f pom.xml -pl services/triage-service -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false test

# Single test class
./services/triage-service/mvnw -f pom.xml -pl services/triage-service -Dtest=CambiarEstadoServiceTest -Dsurefire.failIfNoSpecifiedTests=false test

# Single test method
./services/triage-service/mvnw -f pom.xml -pl services/triage-service -Dtest=CambiarEstadoServiceTest#metodo -Dsurefire.failIfNoSpecifiedTests=false test
```

- No `failsafe` plugin is configured, so `*IT` classes are NOT part of the default `test` phase. Pass `-Dtest='*IT'` explicitly.
- No linter or formatter is configured in the POMs. Do not invent a lint step.
- Full stack (Postgres, Kafka KRaft, Redis, and the three services):
  `docker compose -f deploy/docker/docker-compose.yml --env-file .env up --build`
  `.env` must define `DB_PASSWORD`, `API_KEY`, and `LLM_API_KEY` (compose fails fast without them).
- Host ports: triage 8081, assistant 8082, alert 8083 (all map to container 8080), Postgres 5432, Kafka external 29092, Redis 6379.

## Architecture

**Modules.** The root `pom.xml` aggregates `contracts/events-contract` and the three services (`triage-service`, `assistant-service`, `alert-service`). `triage-service` depends on the contract module for event types (other services: not verified yet). Its version is pinned in the root `dependencyManagement`, so keep the version in sync when changing it.

**Maturity is uneven.** Only `triage-service` has real logic. `assistant-service` and `alert-service` currently contain only the Spring Boot entry point and a minimal `application.yaml`. Do not assume behavior for them from the compose file or the naming.

**Triage service is hexagonal, and the rule is enforced.** `HexagonalArchitectureTest` (ArchUnit) runs as a normal unit test and fails the build on violations. Layers:
- `domain` — pure model (`Paciente`, `EventoClinico`, `NivelTriaje`, `EstadoPaciente`, `TriageClassifier`). No Spring, R2DBC, Kafka, or Jackson.
- `application` — use cases (`port.in`) implemented by services (`application.service`), and outbound ports (`port.out`) such as `PacienteRepositoryPort`, `OutboxPort`, `ClockPort`, `SlaPolicy`.
- `infrastructure` — adapters. `adapter.in` (web controller, scheduler) talks only to use case interfaces. `adapter.out` (R2DBC repositories, outbox) implements ports. Adapters never depend on each other across in/out.
- `infrastructure.config` — Spring wiring, security, Kafka sender, and filters.

**Write path and the outbox.** `RegistrarPacienteService`, `CambiarEstadoService`, and `EscalarPacientesVencidosService` are `@Transactional` (reactive). Each state change writes the aggregate, an audit row (immutable, see `V2__auditoria_inmutable.sql`), and an outbox row (`V3__outbox.sql`) in the same transaction. The outbox row is the only thing that is supposed to reach Kafka. Events go out through the outbox, not directly from the service.

**The outbox relay is NOT implemented yet.** `application.yml` defines `triage.outbox.poll-interval` and `batch-size`, and `KafkaConfig` declares a `KafkaSender` bean. Nothing reads the outbox table or uses that sender. Keep this in mind before assuming events reach `triaje.eventos`. Building the relay is the missing piece of the event flow.

**SLA escalation.** `SlaMonitorScheduler` (inbound adapter) periodically calls `EscalarPacientesVencidosUseCase`. The interval comes from `TRIAGE_SLA_EVALUATION_INTERVAL` (compose default `30s`). SLA minutes per `NivelTriaje` live in `triage.sla.minutos` and are read by `ConfiguredSlaPolicy`. Keys must match enum names exactly (RN-02 in the config comment).

**Persistence: two datasources on purpose.** Runtime uses R2DBC (`r2dbc:postgresql://`). Flyway uses JDBC (`jdbc:postgresql://`) because it does not support R2DBC. Migrations live in `services/triage-service/src/main/resources/db/migration`. Add new migrations there; do not edit applied ones.

**Cross-cutting HTTP concerns.**
- `ApiKeyAuthFilter` checks the `X-API-Key` header against `API_KEY` in constant time. Actuator probes are exempt.
- `CorrelationIdWebFilter` reads or creates `X-Correlation-Id` and echoes it back.
- `GlobalErrorHandler` maps domain exceptions to `application/problem+json`.

**Event contract.** `contracts/events-contract` holds `EventEnvelope`, `EventType`, `PacienteEventPayload`, and `Topics`. The topic `triaje.eventos` (3 partitions) and its DLT `triaje.eventos.dlt` are created by the `kafka-init` container in compose, not by the application. Outbox `aggregate_id` (the patient id) is the Kafka partition key.

**Tests.** `IntegrationTestBase` and `TestcontainersConfiguration` start Postgres and Kafka through Testcontainers and wire them with `@ServiceConnection`. Unit tests in `application`, `domain`, and `infrastructure` use plain mocks and do not need Docker.

## Conventions

- Domain vocabulary and code comments are in Spanish (`Paciente`, `Triaje`, `Escalamiento`, `CambiarEstado`). Match that when extending the domain. Keep new public API names consistent with what is already there.
- Java 21 and Jackson 2 (`com.fasterxml.jackson.*`) come with Spring Boot 3.5. `tools.jackson` (Jackson 3) only exists in Boot 4 and must not be used (ADR-0010).
- Spring Boot 3.5 + WebFlux: the stack is reactive end to end. Do not add blocking JDBC calls to request paths.
