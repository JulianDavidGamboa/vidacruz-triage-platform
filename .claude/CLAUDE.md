# VidaCruz — Sistema de Triaje Digital

Backend reactivo para el triaje de urgencias: registra pacientes, los clasifica con reglas clínicas, controla SLA y escala a quienes los superan, con auditoría completa. Es una prueba técnica "Dev. WebFlux" (nivel Senior / Arquitecto).

Fuentes de verdad, en este orden: [`docs/especificacion.md`](../docs/especificacion.md) (qué debe hacer), [`docs/adr/`](../docs/adr/README.md) (por qué), [`docs/enunciado-prueba.md`](../docs/enunciado-prueba.md) (requisito original). Si el código contradice la especificación, dilo antes de cambiar nada.

## Stack

Java 21, Spring Boot **3.5.x** (el enunciado exige Boot 3; no migrar a Boot 4), WebFlux, Spring Security, Spring Data R2DBC (PostgreSQL), Flyway, reactor-kafka, Micrometer/Prometheus, Testcontainers, ArchUnit. Para el asistente: Spring AI 1.1.x y Resilience4j.

## Estructura

```text
contracts/events-contract/     # Contrato de eventos: jar plano, solo records y constantes
services/triage-service/       # RF-01, RF-02, RF-03 y RF-05
services/assistant-service/    # RF-04
services/alert-service/        # RF-06 (Teams y Discord)
deploy/docker/                 # docker-compose.yml y Dockerfile genérico (ARG SERVICE)
deploy/k8s/                    # Kustomize: base, data y overlays/azure
deploy/azure/                  # Bicep (ACR y AKS) y guía de despliegue
docs/                          # especificación y ADR
```

Cada servicio: `domain` ← `application` ← `infrastructure` (hexagonal).

## Comandos

```bash
./mvnw test                  # unitarias + ArchUnit, sin Docker
./mvnw verify                # además los *IT (requieren Docker)
./mvnw -pl services/triage-service -am verify
cp .env.example .env         # luego completar DB_PASSWORD y API_KEY
docker compose -f deploy/docker/docker-compose.yml --env-file .env up --build \
  postgres kafka kafka-init redis triage-service
```

`mvnw` y `.mvn/` deben existir en la raíz (`mvn -N wrapper:wrapper` si faltan). Nunca declares un módulo en el pom padre antes de que exista.

## Reglas de arquitectura (las verifica `HexagonalArchitectureTest`)

- **`domain`:** solo `java.*` y su propio paquete. Sin Spring, Reactor, Jakarta, Jackson, R2DBC ni Kafka. Los invariantes viven aquí (`SignosVitales`, `Paciente`); el DTO web repite las mismas validaciones solo para dar mensajes por campo.
- **`application`:** puede usar Reactor y anotaciones de Spring (`@Service`, `@Transactional`); nunca importa `infrastructure`. Los puertos (`port.in`, `port.out`) son interfaces; los `Command` y `Vista` son records.
- **`infrastructure`:** los adaptadores de entrada (`adapter.in`) solo conocen puertos `in`, no `application.service`; `adapter.in` y `adapter.out` no se conocen entre sí. Entidades y mappers de BD o de mensajes viven aquí; el dominio no conoce ninguna entidad.
- El tiempo siempre viene de `ClockPort`, nunca de `Instant.now()` en dominio o aplicación.

## Patrones que hay que respetar

- **Outbox transaccional:** todo cambio clínico escribe paciente, auditoría y `outbox_events` en **una** transacción; el relay publica a Kafka (at-least-once, clave = `pacienteId`). Nunca publiques a Kafka directamente desde un caso de uso. Los consumidores deduplican por `eventId`.
- **Control optimista de estado:** `UPDATE ... WHERE id = :id AND estado = :esperado`; 0 filas ⇒ `EstadoConcurrenteException` (409), sin auditar ni publicar.
- **Escalamiento idempotente (RN-03):** una sola sentencia `UPDATE ... RETURNING` con `FOR UPDATE SKIP LOCKED`. "Escalado" es un flag, no un estado.
- **Auditoría (RN-04):** append-only, con trigger que bloquea `UPDATE` y `DELETE`. No crear endpoints ni puertos de borrado.
- **Errores:** las excepciones de dominio extienden `DomainException`; `GlobalErrorHandler` las traduce a RFC 7807 (400, 404, 409). Nunca filtres detalles internos en un 500.
- **Métricas:** `triage.clasificacion{nivel}` y `triage.escalamientos`. Las nuevas pasan por `MetricsPort`.

## Trampas conocidas

- **Event loop:** sin llamadas bloqueantes. Spring AI (`ChatClient.call()`, `VectorStore.similaritySearch()`) bloquea: envuélvelo en `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.
- **R2DBC:** una conexión no admite otra consulta mientras un resultado sigue abierto; materializa con `collectList()` antes de escribir (ver `EscalarPacientesVencidosService`). Los inserts con id asignado por el dominio usan `template.insert()`, no `save()`.
- **Flyway** no usa R2DBC: necesita `spring.flyway.url` JDBC propio.
- **No agregar `spring-boot-starter-web`:** con WebFlux en el classpath, Boot arrancaría en modo servlet.
- **`events-contract`:** jar plano, sin `spring-boot-maven-plugin`.
- **SLA en `application.yml`:** las claves son el nombre exacto del enum (`CRITICO`, `URGENTE_2`…).
- **Tests de integración:** extienden `IntegrationTestBase`, se llaman `*IT` y corren con Failsafe (`verify`). Para probar tiempo, usa un `ClockPort` mutable y apaga el scheduler con `triage.sla.monitor-enabled=false`.

## Convenciones

- Nombres del dominio y mensajes de error en **español**; código de infraestructura y comentarios técnicos pueden mezclar.
- Los rangos de validación (tabla del enunciado) están en tres sitios con los mismos valores: DTO, dominio y `CHECK` de la migración. Cambia los tres o ninguno.
- Migraciones Flyway: `V<n>__descripcion.sql`. **Nunca edites una migración ya aplicada**; crea la siguiente (`V5…`).
- ADR: son inmutables una vez aceptados. Si una decisión cambia, crea uno nuevo y marca el anterior como "Reemplazada por ADR-XXXX". Actualiza el índice `docs/adr/README.md`.
- Pruebas: unitarias `*Test` (Mockito + StepVerifier, AssertJ), integración `*IT`. Cada requisito nuevo trae sus pruebas y se agrega a la tabla de trazabilidad de la especificación.
- Commits pequeños y frecuentes, con mensaje que diga el requisito (`RF-05: reporte por hora con percentile_cont`).

## Seguridad

- Nunca versionar ni leer `.env`, `API_KEY`, `LLM_API_KEY`, `TEAMS_WEBHOOK_URL` ni credenciales de BD. Solo `.env.example` lleva nombres sin valores.
- Todos los endpoints exigen `X-API-Key`, salvo `/actuator/health/**`. Un endpoint nuevo queda protegido por defecto; no abras excepciones sin ADR.
- La alerta de RF-06 no incluye datos personales ni clínicos, solo identificadores y datos operativos.

## Estado y siguiente trabajo

Implementado: RF-01 a RF-06, el relay del outbox, la retención del outbox, el `correlationId` en el MDC y el despliegue en AKS con CI/CD. El detalle y su estado de verificación están en `docs/especificacion.md` (§3 y §14). Pendiente:

1. Key Vault para los secretos (ADR-0009); hoy viven en un `Secret` de Kubernetes.
2. Persistir los vectores del asistente: un arranque con el proveedor de embeddings caído deja el asistente en 503.
3. Verificar el webhook real de Teams (Discord ya está verificado).
4. Test unitario del mapper de pacientes (`PacienteEntityMapper`).

Al terminar un requisito, actualiza el estado en `README.md`, la tabla de `docs/especificacion.md` y el ADR correspondiente.

## Cómo trabajar aquí

- Verifica con `./mvnw verify` antes de dar algo por terminado; si no puedes ejecutarlo, dilo explícitamente en lugar de afirmar que funciona.
- Lee `docs/especificacion.md` y el ADR relevante antes de tocar un requisito.
- Mantén el cambio acotado al requisito pedido; si ves otro problema, repórtalo sin arreglarlo en el mismo cambio.
