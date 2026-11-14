# Especificación funcional — Sistema de Triaje Digital VidaCruz

Este documento describe qué debe hacer el sistema. Es la primera fuente de verdad del proyecto; el porqué de cada decisión está en los [ADR](adr/README.md) y el requisito original en `enunciado-prueba-dev-webflux.md` (enunciado de la prueba técnica).

Convenciones del documento:

- Los nombres del dominio, los campos JSON y los identificadores están en español, como en el código.
- Cuando el enunciado y el código difieren, el texto describe lo que hace el código y lo marca como **Discrepancia**.
- Cuando algo no está definido en el enunciado, se indica como **Supuesto** o **Ambigüedad**.
- El estado de verificación se declara por separado del estado de implementación (ver §3).

## 1. Contexto y objetivo

VidaCruz es un grupo de salud con 12 salas de urgencias. El triaje se hace hoy de forma manual: la enfermera evalúa los signos vitales en recepción y anota la prioridad en una hoja. El enunciado identifica tres problemas:

1. La clasificación varía según quién la hace.
2. No hay trazabilidad de lo ocurrido con cada paciente ni de su tiempo de espera.
3. Los médicos no tienen a mano los protocolos clínicos internos al decidir.

Objetivos del sistema:

- Registrar pacientes con sus signos vitales y clasificarlos con reglas objetivas (RN-01).
- Controlar el tiempo de atención por nivel (SLA, RN-02) y escalar automáticamente a quien lo supera (RN-03).
- Dejar auditoría clínica completa e inmutable (RN-04).
- Permitir consultar protocolos clínicos en lenguaje natural con IA generativa, sin inventar contenido (RN-05).
- Alertar al equipo médico cuando un paciente es escalado (RF-06).

Es un sistema de backend (APIs y eventos). No incluye frontend.

## 2. Alcance, servicios y límites

### 2.1 Servicios

La plataforma son tres servicios Java reactivos (Spring Boot 3.5, WebFlux) y un módulo de contrato, comunicados solo por eventos en Kafka (ADR-0001).

| Componente | Responsabilidad | Dependencias externas |
|------------|-----------------|-----------------------|
| `triage-service` | Registro, clasificación, estados, SLA y escalamiento, auditoría, reporte de operación | PostgreSQL, Kafka |
| `assistant-service` | Consulta RAG de protocolos y vista de urgencias en tiempo real | Kafka, Redis, proveedor de LLM y de embeddings |
| `alert-service` | Alerta al equipo médico ante `paciente_escalado` | Kafka, webhooks de Teams y Discord |
| `contracts/events-contract` | Envelope, tipos de evento, payload y topics. Solo records y constantes, sin lógica | — |

Reglas de dependencia:

- `triage-service` no llama ni espera a los otros servicios: solo publica eventos. Su disponibilidad no depende del asistente ni de las alertas.
- `assistant-service` reconstruye la vista de urgencias consumiendo `triaje.eventos`; no llama a la API de triaje.
- Cada servicio es hexagonal (`domain` ← `application` ← `infrastructure`); las reglas las verifica ArchUnit en cada build (ADR-0002).

### 2.2 Fuera de alcance

- Frontend o interfaz de usuario.
- Identidad por usuario y roles: la autenticación es una API key compartida (ADR-0008), por lo que la auditoría registra eventos, no quién los originó.
- Acuse de recibo o escalamiento de guardia para las alertas (ADR-0007).
- Modelo de sala o sede: el modelo de datos no incluye sala; hay un único canal de alertas para las 12 salas.
- Datos de pacientes reales. El despliegue en Azure es una demostración y no es apto para datos clínicos (ADR-0012).
- Trazado distribuido (OpenTelemetry), considerado bonus por el enunciado: no hay evidencia de él en el código.

## 3. Requisitos y estado

Estado de implementación según el código del repositorio. Estado de verificación según lo declarado en el README y los ADR, y lo que se pudo confirmar en las pruebas.

| ID | Requisito | Servicio | Implementación | Verificación | ADR |
|----|-----------|----------|----------------|--------------|-----|
| RF-01 | Registro y clasificación automática (`POST /triage/pacientes`) | `triage-service` | Implementado | Unitarias y `RegistroPacienteIT` | 0002, 0003 |
| RF-02 | Flujo de estados y consulta (`PATCH .../estado`, `GET .../{id}`) | `triage-service` | Implementado | Unitarias y `FlujoEstadosIT` | 0003 |
| RF-03 | Monitoreo de SLA y escalamiento automático | `triage-service` | Implementado | Unitarias y `EscalamientoIT` (idempotencia y concurrencia) | 0004 |
| RF-04 | Asistente RAG (`POST /asistente/consulta`) y estado de urgencias (`GET /asistente/estado-urgencias`) | `assistant-service` | Implementado | ITs de Redis, HTTP y Kafka. Respuestas con LLM real dependen de OpenRouter (ver §14) | 0005, 0006 |
| RF-05 | Reporte de operación por hora (`GET /triage/reportes/operacion`) | `triage-service` | Implementado | `ReporteOperacionIT` contra PostgreSQL 16 | 0011 |
| RF-06 | Alertas de escalamiento al equipo médico | `alert-service` | Implementado (Teams y Discord, respaldo en log y DLT) | Discord real verificado en AKS; Teams real sin verificar | 0007, 0013 |
| — | Docker Compose, manifests de Kubernetes y despliegue en Azure | `deploy/` | Implementado | Desplegado en AKS (demostración) | 0009, 0012 |

Notas de verificación:

- Los `*IT` usan Testcontainers y requieren un runtime de contenedores. El workflow de CI (`.github/workflows/ci.yml`) ejecuta `*Test` y `*IT` en cada push y pull request. En local, la raíz no tiene `mvnw` ni Failsafe: los `*IT` se ejecutan con `-Dtest='*IT'`.
- La última corrida local declarada en el README (Podman, no Docker Desktop) fue de 175 pruebas en verde (149 unitarias, 6 de arquitectura, 26 de integración). Esa cifra proviene del README y no se reejecutó para este documento.
- El envío real al webhook de Teams no se ha verificado (ADR-0013): el canal solo está probado con un servidor simulado.

## 4. Detalle de requisitos y RF-05: reporte de operación por hora

Las secciones 4 a 9 detallan cada requisito funcional. Esta sección abre con RF-05 por ser el requisito de SQL avanzado; RF-01 a RF-04 y RF-06 siguen en §5 a §9.

### 4.1 Endpoint

`GET /triage/reportes/operacion?desde=<ISO-8601>&hasta=<ISO-8601>`

- `desde` y `hasta` son obligatorios e instantes ISO 8601. Si falta alguno, la respuesta es 400.
- El rango es semiabierto: `[desde, hasta)`.
- Si `desde` es igual o posterior a `hasta`, la respuesta es 400 (`RangoReporteInvalidoException`) y no se consulta la base de datos.

### 4.2 Contenido

El reporte agrupa por hora de **registro** del paciente, en UTC (ADR-0011). Cada franja contiene:

| Campo | Significado |
|-------|-------------|
| `hora` | Inicio de la hora (UTC) |
| `totalRegistrados` | Pacientes registrados en esa hora |
| `porNivel` | Conteo por nivel de triaje |
| `tiempoPromedioEsperaMin` | Promedio de la espera registro → primera entrada a consulta, en minutos |
| `p95EsperaMin` | Percentil 95 de esa espera, en minutos |
| `rankingVolumen` | Posición de la franja por volumen (`RANK() OVER (ORDER BY total DESC)`) |

La respuesta envuelve las franjas junto con `desde` y `hasta`. Las horas sin pacientes registrados no aparecen. Las franjas salen ordenadas por hora.

### 4.3 Reglas

- Los pacientes que nunca entraron a consulta (incluida el alta directa, que no fija `fecha_primera_consulta`) cuentan en el total y en el conteo por nivel, pero no en el promedio ni en el p95. Si ningún paciente de la franja llegó a consulta, ambos valores son `null`.
- La espera se calcula desde `fecha_primera_consulta`, que se fija una sola vez en la primera entrada a `EN_CONSULTA`.
- Todo el cálculo ocurre en PostgreSQL: una CTE `base` calcula la espera por paciente y una CTE `por_hora` agrega con `COUNT(*) FILTER`, `AVG` y `percentile_cont(0.95) WITHIN GROUP`. El enunciado prohíbe iterar en memoria para agregar.
- No requiere migración propia: usa el índice `idx_pacientes_fecha_registro` (V4).

**Discrepancia menor:** el enunciado no pide `rankingVolumen`; el código lo añade para usar una window function (`RANK() OVER`), que el enunciado cita como técnica aceptada.

**Ambigüedad resuelta:** el enunciado no dice si agrupar por hora de registro o de consulta. Se agrupa por registro, porque el reporte describe la demanda que llega (ADR-0011).

**Limitación:** la franja es UTC fijo; un reporte en hora local requeriría un parámetro de zona horaria. El costo con volumen real no se ha medido (ADR-0011).

## 5. RF-01: registro y clasificación

### 5.1 Endpoint

`POST /triage/pacientes` con cuerpo JSON:

| Campo | Tipo | Obligatorio | Rango o límite válido |
|-------|------|:-----------:|-----------------------|
| `nombreCompleto` | texto | Sí | 3 a 100 caracteres |
| `edad` | entero | Sí | 0 a 120 años |
| `frecuenciaCardiaca` | entero | Sí | 20 a 250 bpm |
| `presionSistolica` | entero | Sí | 60 a 260 mmHg |
| `presionDiastolica` | entero | Sí | 30 a 160 mmHg y siempre menor que la sistólica |
| `spo2` | entero | Sí | 70 a 100 % |
| `temperatura` | decimal | Sí | 34.0 a 42.0 °C |
| `dolor` | entero | Sí | 0 a 10 |
| `gcs` | entero | Sí | 3 a 15 |
| `quejaPrincipal` | texto | Sí | No vacío; máximo 300 caracteres |

**Discrepancia menor:** el límite de 300 caracteres en `quejaPrincipal` no figura en el enunciado; lo imponen el DTO y el dominio (`Paciente.registrar`).

Los rangos viven en tres sitios con los mismos valores: el DTO web (`RegistrarPacienteRequest`), el dominio (`SignosVitales`) y los `CHECK` de la migración `V1__pacientes.sql`. El DTO existe solo para dar mensajes por campo; el dominio garantiza los invariantes para cualquier adaptador.

### 5.2 Respuesta

Éxito: **201 Created** con cabecera `Location: /triage/pacientes/{id}` y cuerpo:

| Campo | Contenido |
|-------|-----------|
| `id` | UUID asignado por el dominio |
| `nivel` | Nombre del nivel (`CRITICO`, `URGENTE`, `URGENTE_2`, `NO_URGENTE`, `NO_URGENTE_2`) |
| `nivelCodigo` | 1 a 5 |
| `slaMinutos` | SLA del nivel (RN-02) |
| `estado` | `REGISTRADO` |
| `fechaRegistro` | Instante ISO 8601 en UTC |

**Ambigüedad:** el enunciado no fija el código de éxito. El código responde 201, no 200.

Un valor fuera de rango, un campo faltante o la diastólica mayor o igual a la sistólica responden **400** en formato RFC 7807 con la lista `errores` (`campo`, `mensaje`) y el primer mensaje como `detail`. Un JSON mal formado también responde 400.

### 5.3 Clasificación (RN-01)

`TriageClassifier` evalúa en orden de gravedad; la primera regla que se cumple fija el nivel, de modo que si se cumplen condiciones de varios niveles rige el más grave.

| Nivel | Código | Condiciones (cualquiera) |
|-------|:------:|--------------------------|
| `CRITICO` | 1 | FC < 40 o FC > 120, o SpO2 < 90, o presión sistólica < 90, o GCS <= 8 |
| `URGENTE` | 2 | 90 <= SpO2 <= 93, o 90 <= sistólica <= 100, o temperatura >= 38.5, o 9 <= GCS <= 13 |
| `URGENTE_2` | 3 | 90 <= FC <= 120, o 38.0 <= temperatura < 38.5, o dolor >= 7 |
| `NO_URGENTE` | 4 | Ninguna de las anteriores |
| `NO_URGENTE_2` | 5 | Cumple nivel 4 y la queja principal es administrativa |

Quejas administrativas: `documentos`, `recolección de resultados`, `renovación de recetario`, `vacunación`, `seguimiento`.

Comportamiento de la comparación de la queja (decisión de implementación, no del enunciado):

- Se normaliza a minúsculas, sin tildes y con espacios colapsados antes de comparar.
- La coincidencia es con la frase completa. Una queja que solo contiene una palabra administrativa no cuenta.
- Una queja administrativa con signos vitales anormales no baja de nivel: el nivel 5 solo aplica cuando se llega al nivel 4.
- Una queja nula se trata como no administrativa.

Pacientes de ejemplo del enunciado (E1 a E6) y su nivel esperado: E1 `CRITICO`, E2 `URGENTE`, E3 `URGENTE_2`, E4 `NO_URGENTE`, E5 `NO_URGENTE_2`, E6 `CRITICO`. Están cubiertos por `TriageClassifierTest`.

### 5.4 Efectos

En una sola transacción: se guarda el paciente (estado `REGISTRADO`, sin escalar, SLA congelado del nivel), se escribe la auditoría y se escribe el evento `paciente_registrado` en `outbox_events`. Se incrementa la métrica `triage.clasificacion{nivel}`. El evento llega a Kafka después, por el relay del outbox (§11).

## 6. RF-02: estados y consulta

### 6.1 Estados y transiciones

Estados: `REGISTRADO`, `EN_CONSULTA`, `ALTA`.

| Origen | Destinos válidos |
|--------|------------------|
| `REGISTRADO` | `EN_CONSULTA`, `ALTA` (alta directa) |
| `EN_CONSULTA` | `ALTA` |
| `ALTA` | ninguno (estado terminal) |

Cualquier otra transición, incluida la que repite el mismo estado, responde **409**. "Escalado" no es un estado, es un flag (ver §7).

### 6.2 Cambio de estado

`PATCH /triage/pacientes/{id}/estado` con cuerpo `{"estado": "REGISTRADO|EN_CONSULTA|ALTA"}`.

| Caso | Respuesta |
|------|-----------|
| Transición válida | 200 con el detalle del paciente |
| Transición inválida (por ejemplo `ALTA` → `EN_CONSULTA`) | 409 con mensaje descriptivo |
| Cambio concurrente de estado | 409, sin auditar ni publicar |
| Paciente inexistente | 404 |
| `estado` ausente o fuera del conjunto, o `id` mal formado | 400 |

Cada transición válida, en una sola transacción:

1. Lee el paciente y valida la transición en el dominio.
2. Actualiza con control optimista: `UPDATE ... WHERE id = :id AND estado = :esperado`. Si afecta 0 filas, responde 409 (`EstadoConcurrenteException`).
3. Registra la auditoría (RN-04).
4. Escribe en el outbox `paciente_en_consulta` o `paciente_alta`.

La primera entrada a `EN_CONSULTA` fija `fecha_primera_consulta` una sola vez. El alta directa no la fija.

La respuesta del `PATCH` usa el mismo cuerpo que el `GET`, pero `minutosEnEstadoActual` vale 0 porque el estado acaba de cambiar.

### 6.3 Consulta

`GET /triage/pacientes/{id}` devuelve:

- Datos del paciente: `id`, `nombreCompleto`, `edad`, `quejaPrincipal` y `signosVitales`.
- Clasificación: `nivel`, `nivelCodigo`, `slaMinutos`.
- `estado` y `minutosEnEstadoActual` (minutos completos, nunca negativo).
- `flags.escalado`.
- Fechas: `fechaRegistro`, `fechaEstado`, `fechaEscalado`, `fechaPrimeraConsulta`.

Responde 404 si el paciente no existe y 400 si el `id` no es un UUID.

## 7. RF-03: SLA y escalamiento

### 7.1 SLA por nivel (RN-02)

Minutos máximos desde el registro hasta entrar a consulta. Se leen de `triage.sla.minutos` en `services/triage-service/src/main/resources/application.yml`; las claves son el nombre exacto del enum.

| Nivel | SLA (minutos) |
|-------|:-------------:|
| `CRITICO` | 5 |
| `URGENTE` | 10 |
| `URGENTE_2` | 30 |
| `NO_URGENTE` | 60 |
| `NO_URGENTE_2` | 120 |

- Los valores se validan al arrancar: si falta un nivel o no es positivo, el servicio no inicia (`ConfiguredSlaPolicy`).
- El SLA se **congela al registrar** en la columna `sla_minutos`: cambiar la configuración no afecta a pacientes ya registrados.

### 7.2 Monitor

`SlaMonitorScheduler` evalúa periódicamente a los pacientes activos y llama a `EscalarPacientesVencidosUseCase`.

| Propiedad | Variable de entorno | Por defecto |
|-----------|---------------------|-------------|
| `triage.sla.evaluation-interval` | `TRIAGE_SLA_EVALUATION_INTERVAL` | `30s` |
| `triage.sla.monitor-enabled` | `TRIAGE_SLA_MONITOR_ENABLED` | `true` |

El intervalo y los SLA son configurables, como exige el enunciado, para probar sin esperar decenas de minutos. Un ciclo lento no encola ticks atrasados, los ciclos de una instancia no se solapan y un error en un ciclo no detiene los siguientes. El escalamiento tiene una latencia de hasta un intervalo de evaluación.

### 7.3 Regla de escalamiento

Un paciente se escala cuando está en `REGISTRADO`, no está escalado y `fecha_registro + sla_minutos < ahora` (comparación estricta: en el instante exacto del vencimiento aún no escala). El instante `ahora` viene de `ClockPort`, no de `now()` de la base.

La operación es una sola sentencia `WITH vencidos AS (... FOR UPDATE SKIP LOCKED) UPDATE pacientes ... RETURNING` (ADR-0004). Por cada fila devuelta, en la misma transacción, se registra la auditoría y se escribe `paciente_escalado` en el outbox. Se incrementa la métrica `triage.escalamientos`.

El escalamiento no cambia el estado: el paciente sigue en `REGISTRADO` con `escalado = true` y `fecha_escalado`. Si luego entra a consulta, conserva el flag.

### 7.4 Idempotencia (RN-03)

- Un paciente se escala una única vez: la condición `estado = 'REGISTRADO' AND escalado = FALSE` hace que la segunda ejecución devuelva cero filas, sin evento ni auditoría duplicados.
- Un paciente en `EN_CONSULTA` o `ALTA` nunca se escala por la misma condición.
- Con varias réplicas, `SKIP LOCKED` evita que dos instancias escalen al mismo paciente.
- Si una transición a consulta y el escalamiento compiten, gana quien confirme primero.

## 8. RF-04: asistente clínico

### 8.1 Consulta con RAG

`POST /asistente/consulta` con cuerpo `{"pregunta": "..."}` (obligatoria, no vacía, máximo 500 caracteres; si no, 400).

Respuesta:

```json
{
  "respuesta": "...",
  "fuentes": [ { "protocolo": "PROTO-001", "seccion": "Medidas iniciales" } ]
}
```

Corpus: `PROTO-001` (crisis hipertensiva), `PROTO-002` (sepsis, qSOFA) y `PROTO-003` (deshidratación pediátrica), en `resources/protocolos/*.md`. Se cargan al arrancar, divididos por sección, en un `SimpleVectorStore` en memoria (ADR-0005). Cada fragmento lleva como metadata `protocolo` y `seccion`.

### 8.2 Política anti-alucinación (RN-05)

Defensa en dos puntos, independiente del comportamiento del modelo:

1. **Antes del LLM.** Se recuperan los `top-k` fragmentos (3 por defecto). Los que no alcanzan el umbral `assistant.rag.similarity-threshold` se descartan. Si no queda ninguno, la respuesta es el aviso fijo `No existe un protocolo disponible en el corpus para responder esta pregunta.` con `fuentes` vacío y **sin llamar al LLM**.
2. **Después del LLM.** Las `fuentes` se construyen desde la metadata de los fragmentos recuperados, nunca del texto generado. Si el texto del modelo está vacío o cita un `PROTO-nnn` que no está entre los recuperados, se devuelve el mismo aviso fijo, sin contenido clínico.

Además, el dominio lo impone: `RespuestaAsistente` rechaza construir una respuesta sin fuentes salvo que sea el aviso fijo.

El prompt restringe al modelo a los protocolos recibidos, pide citar con `[PROTO-nnn]` y usa temperatura 0.

Consultas de ejemplo del enunciado: la de crisis hipertensiva debe responder con `PROTO-001`; la de sepsis, con `PROTO-002`; la de diabetes tipo 2 debe devolver el aviso sin contenido clínico.

**Umbral de similitud.** El valor por defecto en `application.yaml` es 0.75. El `ConfigMap` del despliegue en AKS lo sobrescribe con `0.40` y usa otro modelo de embeddings (`LLM_EMBEDDING_MODEL`). El ADR-0005 lo declara "a calibrar" y no registra el valor final. El Docker Compose no define esas variables, así que en local rigen los valores por defecto. Ver §14.

### 8.3 Modo degradado (ADR-0006)

- Resilience4j protege las llamadas de chat (`llm`) y de embeddings (`embeddings`): ventana de 10 llamadas, umbral de fallo del 50 %, 30 s en estado abierto y 3 llamadas de prueba en semiabierto. Cada llamada tiene además un timeout de 10 s.
- Con circuito abierto, timeout, error del proveedor, respuesta vacía del proveedor o corpus aún no cargado, la API responde **503** con `application/problem+json` y el detalle `Asistente clínico en modo degradado: el proveedor de IA no está disponible`.
- Las llamadas bloqueantes de Spring AI se aíslan con `subscribeOn(Schedulers.boundedElastic())`.
- No hay reintento automático de la consulta clínica.
- La respuesta "no existe protocolo" no cuenta como fallo del circuito, porque no llama al LLM.
- `GET /asistente/estado-urgencias` no depende del LLM y sigue operativo en modo degradado.
- El triaje no depende del asistente.

### 8.4 Estado de urgencias en tiempo real

`GET /asistente/estado-urgencias` devuelve `{"total": n, "porNivel": {"CRITICO": n, ...}}` con los cinco niveles siempre presentes (0 si no hay pacientes).

- Cuenta los pacientes activos: todos los que no están en `ALTA`.
- Se reconstruye consumiendo `triaje.eventos` (grupo `asistente-urgencias`, `auto.offset.reset=earliest`), sin llamar a la API de triaje. La vista es un hash de Redis `urgencias:activos` (clave: `pacienteId`, valor: código de nivel).
- `paciente_registrado` y `paciente_en_consulta` activan al paciente; `paciente_alta` lo quita; `paciente_escalado` no cambia la vista.
- La actualización es idempotente por construcción (hash por `pacienteId`), por lo que repetir un evento no duplica al paciente. No hay deduplicación explícita por `eventId` (ver §14).

## 9. RF-06: alertas de escalamiento

El enunciado deja el canal, el formato y la tecnología abiertos. Las decisiones están en ADR-0007 y ADR-0013.

### 9.1 Comportamiento

- `alert-service` consume `triaje.eventos` (grupo `alertas-escalamiento`, `earliest`) y procesa solo `paciente_escalado`; ignora el resto.
- Canales soportados, cada uno activo solo si su URL está configurada:
  - Webhook de Microsoft Teams (`TEAMS_WEBHOOK_URL`), cuerpo `{"text": ...}`.
  - Webhook de Discord (`DISCORD_WEBHOOK_URL`), cuerpo `{"content": ...}`, recortado a 2000 caracteres.
- `CanalAlertasCompuesto` envía a todos los canales configurados en paralelo. Si al menos uno entrega, el resultado es `ENVIADA`; si todos fallan, `REGISTRADA_EN_LOG`.
- Respaldo: un log estructurado a nivel `WARN` con el marcador `ALERTA_ESCALAMIENTO`.
- Sin ningún webhook configurado (modo local), la alerta queda solo en el log y no va al DLT.
- Si todos los canales configurados fallan, la alerta queda en el log y el registro original se publica en `triaje.eventos.dlt`.

### 9.2 Resiliencia

Por canal: timeout de 10 s, 2 reintentos con espera creciente (inicio de 200 ms) y circuit breaker propio (ventana de 10, 50 %, 30 s abierto, 3 en semiabierto). Un circuito abierto no reintenta. Un canal caído no bloquea al otro.

### 9.3 Deduplicación

La entrega Kafka es at-least-once. Una caché en memoria de `eventId` recientes (`alert.dedupe-capacity`, 10 000 por defecto) evita alertas repetidas. Al ser en memoria, puede haber duplicados tras un reinicio; se acepta porque perder una alerta es peor que duplicarla.

### 9.4 Contenido de la alerta

Solo identificadores y datos operativos, sin datos personales ni clínicos: id del paciente, nivel (nombre y código), SLA, minutos excedidos y el instante `occurredAt` del evento. El texto termina remitiendo a la API de triaje.

Los minutos excedidos se calculan como `occurredAt - (fechaRegistro + slaMinutos)`, nunca negativo. En eventos versión 1, que no traen SLA ni fecha de registro, la alerta se envía sin esos datos.

### 9.5 Verificación

- Discord: envío real verificado en AKS el 2026-11-13 (ADR-0013).
- Teams: sin verificar contra un webhook real; solo servidor simulado (`CanalTeamsTest`).
- El formato del mensaje es texto simple; el formato final depende del tipo de webhook de Teams configurado (ADR-0007).

## 10. Reglas de negocio

Las reglas RN-01 a RN-05 provienen del enunciado y no hay otras en el código ni en los ADR.

| ID | Regla | Dónde se aplica |
|----|-------|-----------------|
| RN-01 | Clasificación por nivel de triaje; gana el nivel de mayor gravedad | `TriageClassifier` (§5.3) |
| RN-02 | SLA por nivel: 5, 10, 30, 60 y 120 minutos; configurable | `ConfiguredSlaPolicy`, `triage.sla.minutos` (§7.1) |
| RN-03 | Escalamiento una única vez; nunca en `EN_CONSULTA` ni `ALTA` | `EscalamientoR2dbcAdapter` (§7.4) |
| RN-04 | Auditoría clínica completa y no borrable por la API | Tabla `auditoria_clinica` con trigger (§10.1) |
| RN-05 | Respuesta del asistente con fuente válida del corpus; sin coincidencia, solo el aviso | `ConsultarAsistenteService`, `RespuestaAsistente` (§8.2) |

### 10.1 Auditoría (RN-04)

Todo cambio de estado o de flag (registro, entrada a consulta, escalamiento, alta) escribe una fila en `auditoria_clinica` con: instante (`ts`), `paciente_id`, `estado_anterior`, `estado_nuevo`, `tipo_evento` y `correlation_id`.

- La tabla es append-only: un trigger (`trg_auditoria_inmutable`) rechaza `UPDATE` y `DELETE` incluso con acceso directo a la base.
- No existe endpoint ni puerto de borrado.
- En el registro, `estado_anterior` es nulo. En el escalamiento, el estado anterior y el nuevo son ambos `REGISTRADO` y el tipo de evento identifica el cambio.
- La auditoría no identifica al usuario que originó el cambio (ADR-0008).
- No hay una prueba automatizada que ejerza el trigger de inmutabilidad (ver §14.4).

## 11. Eventos y contrato

### 11.1 Topics

| Topic | Particiones | Uso |
|-------|:-----------:|-----|
| `triaje.eventos` | 3 | Eventos del paciente. Clave de partición: `pacienteId` |
| `triaje.eventos.dlt` | 1 | Mensajes que un consumidor no pudo procesar (lo usa `alert-service`) |

Los crea el contenedor `kafka-init` en Compose y un `Job` en Kubernetes, no la aplicación. Los nombres están en `Topics`.

### 11.2 Envelope

JSON con envelope versionado (`EventEnvelope`, `CURRENT_VERSION = 2`):

| Campo | Contenido |
|-------|-----------|
| `eventId` | UUID del evento; sirve para deduplicar |
| `type` | `paciente_registrado`, `paciente_en_consulta`, `paciente_escalado`, `paciente_alta` |
| `version` | Versión del contrato; hoy 2 |
| `occurredAt` | Instante del hecho |
| `correlationId` | Correlación de la petición, o `sla-<uuid>` en eventos del monitor |
| `payload` | `PacienteEventPayload` |

Payload (`PacienteEventPayload`): `pacienteId`, `nivel` (código 1 a 5), `estadoAnterior` (nulo en el registro), `estadoNuevo`, `slaMinutos` y `fechaRegistro`. Los dos últimos se añadieron en la versión 2 y son nulos en mensajes versión 1; los consumidores deben tolerarlo.

Mapeo de eventos:

| Evento | Se emite cuando |
|--------|-----------------|
| `paciente_registrado` | Registro (RF-01) |
| `paciente_en_consulta` | Transición a `EN_CONSULTA` (RF-02) |
| `paciente_alta` | Transición a `ALTA` (RF-02) |
| `paciente_escalado` | Escalamiento por SLA (RF-03) |

`EventEnvelopeMapperTest` fija la forma del JSON.

### 11.3 Transactional Outbox (ADR-0003)

- Paciente, auditoría y fila de `outbox_events` se escriben en una transacción. Nunca se publica a Kafka directamente desde un caso de uso.
- `OutboxRelay` lee los pendientes con `FOR UPDATE SKIP LOCKED` ordenados por `seq`, los publica y marca `published_at` en la misma transacción. Si Kafka falla, hace rollback y reintenta en el siguiente ciclo.
- Productor con `acks=all` e idempotencia habilitada (`KafkaConfig`).
- Sondeo cada 500 ms, lotes de 100 (`triage.outbox.poll-interval`, `batch-size`).
- Garantía at-least-once. Los consumidores deben tolerar duplicados.
- Depuración: las filas publicadas se borran pasados 7 días (`triage.outbox.retention`), con ejecución cada hora (`purge-interval`); se apaga con `triage.outbox.purge-enabled=false`.

## 12. Seguridad y errores

### 12.1 Autenticación (ADR-0008)

- Todos los endpoints exigen el header `X-API-Key`, salvo `/actuator/health/**`, que es público para los probes de Kubernetes. `/actuator/prometheus` exige la clave.
- La clave llega por la variable `API_KEY`; el servicio de triaje no arranca si falta. La comparación es en tiempo constante.
- Sin sesiones ni cookies; CSRF, login de formulario y HTTP Basic deshabilitados.
- Sin clave válida: **401** con `application/problem+json`.
- Un endpoint nuevo queda protegido por defecto; abrir una excepción requiere un ADR.
- Las credenciales (`API_KEY`, `LLM_API_KEY`, webhooks, contraseña de base de datos) van solo por variables de entorno o `Secret`; nunca se versionan.

### 12.2 Correlación

`X-Correlation-Id` se acepta si cumple `[A-Za-z0-9._-]{1,64}`; si no, se genera un UUID. Se devuelve en la respuesta, se guarda en la auditoría y en los eventos, y se copia al MDC de los logs.

### 12.3 Errores (RFC 7807)

Las excepciones de dominio extienden `DomainException`; `GlobalErrorHandler` las traduce a `application/problem+json`.

| Código | Causa |
|:------:|-------|
| 400 | Campo fuera de rango o faltante (con lista `errores`); JSON mal formado; `estado` desconocido; `id` o parámetros mal formados; rango de reporte inválido |
| 401 | `X-API-Key` ausente o inválida |
| 404 | Paciente inexistente |
| 409 | Transición inválida o cambio concurrente de estado |
| 503 | Asistente en modo degradado (solo `assistant-service`) |
| 500 | Error no controlado; el detalle interno nunca se expone al cliente |

## 13. Requisitos no funcionales y despliegue

### 13.1 Programación reactiva

- Stack reactivo de extremo a extremo: Spring WebFlux, Reactor, R2DBC y reactor-kafka. No se agrega `spring-boot-starter-web` ni llamadas JDBC bloqueantes en las rutas de petición.
- Flyway usa JDBC porque no soporta R2DBC; por eso hay dos datasources (`spring.r2dbc.*` y `spring.flyway.*`).
- Las llamadas bloqueantes de Spring AI se aíslan en `boundedElastic`.
- Los schedulers (monitor de SLA, relay del outbox) usan `Flux.interval` con `onBackpressureDrop` y `concatMap`.
- El tiempo viene siempre de `ClockPort`, no de `Instant.now()` en dominio ni aplicación.

### 13.2 Observabilidad

- Probes `/actuator/health/liveness` y `/readiness` en los tres servicios.
- Métricas Micrometer expuestas en `/actuator/prometheus`: `triage.clasificacion{nivel}`, `triage.escalamientos` y `asistente.llm.latencia`. Las métricas nuevas del triaje pasan por `MetricsPort`.
- Logs JSON (formato `logstash`) con correlación: solo `triage-service` lo configura (ver §14).

### 13.3 Resiliencia

- El triaje no depende del asistente ni de las alertas.
- Circuit breaker y timeout sobre el LLM y los embeddings (§8.3) y sobre cada canal de alertas (§9.2).
- Outbox con reintento automático hacia Kafka (§11.3).
- Control optimista y `SKIP LOCKED` para la concurrencia entre peticiones y réplicas.

### 13.4 Pruebas

| Tipo | Convención | Requiere contenedores |
|------|------------|:---------------------:|
| Unitarias | `*Test` (Mockito, StepVerifier, AssertJ) | No |
| Arquitectura | `HexagonalArchitectureTest` (ArchUnit) en los tres servicios | No |
| Integración | `*IT`, extienden `IntegrationTestBase` y usan Testcontainers | Sí |

Para probar tiempo se usa un `ClockPort` mutable y se apaga el scheduler con `triage.sla.monitor-enabled=false`. El LLM es mockeable detrás de `LlmPort`.

### 13.5 Ejecución local

```text
cp .env.example .env     # completar DB_PASSWORD, API_KEY y LLM_API_KEY
docker compose -f deploy/docker/docker-compose.yml --env-file .env up --build
```

Levanta PostgreSQL 16, Kafka 3.9 en KRaft, la creación de topics, Redis 7 y los tres servicios. Puertos en el host: triaje 8081, asistente 8082, alertas 8083 (todos al 8080 del contenedor), PostgreSQL 5432, Kafka externo 29092, Redis 6379. Compose exige `DB_PASSWORD`, `API_KEY` y `LLM_API_KEY`.

### 13.6 Despliegue en Azure

- AKS con manifests Kustomize (`deploy/k8s/`: `base`, `data` y `overlays/azure`), infraestructura (ACR y AKS) con Bicep (`deploy/azure/`) y workflows de CI y de despliegue manual (`.github/workflows/`).
- PostgreSQL 16, Kafka 3.9 (KRaft) y Redis 7 corren dentro del clúster como `StatefulSet` de una réplica (ADR-0012), que reemplaza parcialmente al ADR-0009. Sin alta disponibilidad ni respaldos.
- Rutas públicas: `/triage/...` al triaje y `/asistente/...` al asistente. El servicio de alertas no tiene entrada pública. El tráfico va por HTTP sin TLS.
- Los Deployment deben mantener `enableServiceLinks: false`: de lo contrario, el Service `redis` inyecta `REDIS_PORT=tcp://...` y rompe `spring.data.redis.port`.
- Key Vault sigue pendiente (ADR-0009).
- El despliegue es una demostración y no es apto para datos clínicos reales.

## 14. Supuestos, ambigüedades y discrepancias

### 14.1 Ambigüedades del enunciado y decisión tomada

| Tema | Decisión | Referencia |
|------|----------|------------|
| Canal y formato de la alerta (RF-06) | Webhooks de Teams y Discord con respaldo en log y DLT; solo datos operativos | ADR-0007, ADR-0013 |
| Código de éxito de `POST /triage/pacientes` | 201 con `Location` | §5.2 |
| Agrupación del reporte (RF-05) | Por hora de registro, en UTC | ADR-0011 |
| Pacientes sin consulta en el reporte | Cuentan en totales, no en tiempos de espera | ADR-0011 |
| Qué significa "escalado" | Flag persistente, no un estado; las transiciones de RF-02 no cambian | ADR-0004 |
| Qué hace el SLA si la configuración cambia | Se congela al registrar | ADR-0004 |
| Forma de comparar la queja administrativa | Frase completa, sin mayúsculas ni tildes | §5.3 |
| Vector store, framework de IA y formato de eventos | `SimpleVectorStore` en memoria, Spring AI, JSON con envelope versionado | ADR-0003, ADR-0005 |
| Autenticación | API key en `X-API-Key` | ADR-0008 |
| Una sola sala o varias | Un único canal de alertas; sin dato de sala | ADR-0007 |

### 14.2 Discrepancias entre el enunciado y el código

1. `quejaPrincipal` tiene un máximo de 300 caracteres que el enunciado no define (§5.1).
2. El reporte incluye `rankingVolumen`, que el enunciado no pide (§4.3).
3. El enunciado pide "logs estructurados con correlación" como requisito de observabilidad general. Solo `triage-service` configura logs JSON (`logging.structured.format.console: logstash`) y un filtro de correlación; `assistant-service` y `alert-service` no tienen esa configuración ni filtro. Los eventos sí propagan `correlationId` entre servicios.
4. El enunciado pide métricas "latencia del LLM": existe como `asistente.llm.latencia`. En el código no hay métricas propias del estado del circuit breaker ni conteo de fallbacks, que menciona el ADR-0006.
5. El enunciado pide una URL de la aplicación desplegada. El README describe el despliegue en AKS y cómo obtener la IP, pero este documento no registra ninguna URL pública ni IP.

### 14.3 Discrepancias entre el código y los ADR o la documentación

1. **Deduplicación por `eventId`.** El ADR-0003 dice que los consumidores deduplican por `eventId`. `alert-service` lo hace (caché en memoria). `assistant-service` no: su vista es idempotente por construcción, y `EventoPaciente` recibe el `eventId` pero no lo usa.
2. **DLT en el asistente.** El ADR-0003 reserva `triaje.eventos.dlt` para mensajes que un consumidor no pueda procesar. Solo `alert-service` lo usa. En `assistant-service`, un evento que falla tras 3 reintentos se registra en el log y se descarta con el offset confirmado.
3. **TimeLimiter.** El ADR-0006 menciona un `TimeLimiter` de 10 s. El código aplica `.timeout(10 s)` de Reactor junto al circuit breaker; el efecto es equivalente, pero no hay `TimeLimiter` de Resilience4j.
4. **Nombre de la métrica del LLM.** El ADR-0006 la llama `assistant.llm.latency`; el código registra `asistente.llm.latencia`.
5. **Readiness del asistente.** El ADR-0005 dice que, si el servicio de embeddings no está disponible al arrancar, el asistente queda con readiness DOWN. El código no define un indicador de salud propio: el `ProtocolCorpusLoader` reintenta hasta 6 veces y, si falla, el asistente responde 503 hasta reiniciar (lo que describe el README).
6. **Umbral de similitud.** El ADR-0005 fija 0.75 como valor inicial a calibrar. El valor por defecto del código es 0.75, pero el `ConfigMap` de AKS usa 0.40 con otro modelo de embeddings, y el Docker Compose no sobrescribe ninguno. Un despliegue local y uno en AKS se comportan distinto con la misma pregunta; no hay una prueba automatizada que use el modelo de embeddings real para los casos del enunciado.
7. **Variables en Compose.** El README documenta `DISCORD_WEBHOOK_URL`, pero `docker-compose.yml` solo pasa `TEAMS_WEBHOOK_URL` a `alert-service`. En Kubernetes la clave de Discord va en el `Secret` `vidacruz-secrets`.
8. **Encabezados de ADR desactualizados.** Los campos de implementación de los ADR-0001, 0005, 0006 y 0008 aún dicen "pendiente" o limitan la implementación a triage, pero el índice (`docs/adr/README.md` y el README del repositorio) los declara implementados. El ADR-0003 muestra un envelope de ejemplo con `"version": 1`; el contrato actual es la versión 2. Los ADR aceptados son inmutables, por lo que no se corrigen aquí.
9. **Instrucciones del proyecto.** `CLAUDE.md` (raíz) afirma que el relay del outbox no está implementado y que `assistant-service` y `alert-service` solo tienen el punto de entrada; `.claude/CLAUDE.md` lista RF-04, RF-05 y RF-06 como pendientes. El código contradice ambas cosas (existen `OutboxRelay`, `ReporteOperacionService` y los dos servicios completos). El comando `.claude/commands/siguiente-rf.md` remite a `docs/adr/0004` para el estilo SQL de RF-05, pero el ADR de RF-05 es el 0011.

### 14.4 Lo que no se pudo verificar

- Las respuestas del asistente con un LLM y un modelo de embeddings reales dependen de OpenRouter (modelos gratuitos); no hay garantía de disponibilidad ni de calidad.
- No hay prueba que abra el circuit breaker del LLM; la prueba del 503 (`AsistenteHttpIT`) cubre el caso de corpus no cargado.
- No hay prueba automatizada del trigger de inmutabilidad de la auditoría.
- No hay medición de rendimiento del reporte (RF-05) con volumen real.
- El webhook real de Teams no se ha probado.
- La corrida de 175 pruebas del README no se reejecutó al escribir esta especificación.

### 14.5 Supuestos

- La alerta es una notificación; no hay acuse ni rotación de guardia.
- Para el reporte, "tiempo de espera" es `fecha_primera_consulta - fecha_registro`, en minutos con fracción.
- El nivel se serializa en los eventos como código numérico (1 a 5) y en la API como nombre y código.
- `PACIENTE_ESCALADO` no altera la vista de urgencias porque el paciente sigue activo en el mismo nivel.

## 15. Trazabilidad

Cada fila une el requisito o regla con su decisión (ADR), las clases principales y las pruebas. Las clases están bajo `services/<servicio>/src/main/java` y `src/test/java`. Los `*IT` requieren un runtime de contenedores.

| Requisito o regla | ADR | Clases principales | Pruebas |
|-------------------|-----|--------------------|---------|
| RF-01 registro | 0002, 0003 | `PacienteController`, `RegistrarPacienteService`, `Paciente`, `SignosVitales`, `RegistrarPacienteRequest`, `GlobalErrorHandler` | `RegistrarPacienteServiceTest`, `PacienteTest`, `SignosVitalesTest`, `RegistrarPacienteRequestValidationTest`, `RegistroPacienteIT` |
| RF-01 / RN-01 clasificación | 0002 | `TriageClassifier`, `NivelTriaje` | `TriageClassifierTest` (E1 a E6, límites, queja administrativa) |
| RF-02 estados y consulta | 0003 | `CambiarEstadoService`, `ConsultarPacienteService`, `EstadoPaciente`, `PacienteR2dbcAdapter` | `CambiarEstadoServiceTest`, `ConsultarPacienteServiceTest`, `TransicionesEstadoTest`, `FlujoEstadosIT` |
| RF-03 / RN-02 SLA | 0004 | `ConfiguredSlaPolicy`, `TriageProperties`, `SlaPolicy` | `ConfiguredSlaPolicyTest` |
| RF-03 / RN-03 escalamiento | 0004 | `EscalarPacientesVencidosService`, `EscalamientoR2dbcAdapter`, `SlaMonitorScheduler`, `Escalamiento` | `EscalarPacientesVencidosServiceTest`, `EscalamientoDominioTest`, `SlaMonitorSchedulerTest`, `EscalamientoIT`, `SlaMonitorSchedulerIT` |
| RN-04 auditoría | 0003 | `AuditoriaR2dbcAdapter`, `EventoClinico`, `V2__auditoria_inmutable.sql` | Verificada indirectamente en `RegistroPacienteIT`, `FlujoEstadosIT` y `EscalamientoIT`; sin prueba directa del trigger |
| Outbox y eventos | 0003 | `OutboxR2dbcAdapter`, `OutboxRelay`, `EventEnvelopeMapper`, `DepurarOutboxService`, `DepuracionOutboxScheduler`, `EventEnvelope`, `PacienteEventPayload` | `EventEnvelopeMapperTest`, `DepurarOutboxServiceTest`, `DepuracionOutboxSchedulerTest`, `DepuracionOutboxIT` |
| RF-04 consulta / RN-05 | 0005, 0006 | `AsistenteController`, `ConsultarAsistenteService`, `RespuestaAsistente`, `SimpleVectorProtocolRetriever`, `ProtocolCorpusLoader`, `ProtocoloParser`, `SpringAiLlmAdapter`, `ResilienceConfig` | `ConsultarAsistenteServiceTest`, `RespuestaAsistenteTest`, `ProtocoloParserTest`, `ProtocolCorpusLoaderTest`, `AsistenteHttpIT` |
| RF-04 estado de urgencias | 0001, 0003 | `ConsultarEstadoUrgenciasService`, `ProcesarEventoPacienteService`, `EventosPacienteConsumer`, `RedisVistaUrgenciasAdapter`, `EstadoUrgencias` | `ProcesarEventoPacienteServiceTest`, `VistaUrgenciasRedisIT`, `EventosKafkaIT` |
| RF-05 reporte | 0011 | `ReporteController`, `ReporteOperacionService`, `ReporteOperacionR2dbcAdapter`, `FranjaHorariaVista` | `ReporteOperacionServiceTest`, `ReporteOperacionIT` |
| RF-06 alertas | 0007, 0013 | `EscalamientoConsumer`, `ProcesarEscalamientoService`, `CanalAlertasCompuesto`, `CanalTeams`, `CanalDiscord`, `CanalWebhook`, `EventosRecientes`, `KafkaDeadLetterAdapter` | `ProcesarEscalamientoServiceTest`, `CanalAlertasCompuestoTest`, `CanalTeamsTest`, `CanalDiscordTest`, `EventoEscalamientoTest`, `EventosRecientesTest`, `EscalamientoKafkaIT`, `AlertServiceContextIT` |
| Seguridad por API key | 0008 | `ApiKeyAuthFilter`, `SecurityConfig` (triage y asistente), `CorrelationIdWebFilter` | `RegistroPacienteIT`, `FlujoEstadosIT`, `AsistenteHttpIT` (casos 401 y health público), `CorrelationIdMdcTest` |
| Arquitectura hexagonal | 0002 | Paquetes `domain`, `application`, `infrastructure` | `HexagonalArchitectureTest` en los tres servicios |
| Despliegue | 0009, 0010, 0012 | `deploy/docker/`, `deploy/k8s/`, `deploy/azure/`, `.github/workflows/` | CI (`ci.yml`); verificación manual en AKS |
| OpenAPI | — | `OpenApiConfig` (triage y asistente) | `OpenApiIT`, `AsistenteHttpIT` |

Al agregar un requisito nuevo, se añade una fila a esta tabla y se actualiza el estado de §3.
