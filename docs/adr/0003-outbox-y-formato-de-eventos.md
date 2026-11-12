# ADR-0003 — Transactional Outbox y formato JSON de eventos

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Implementada en RF-01 (`OutboxR2dbcAdapter`, `OutboxRelay`, `EventEnvelopeMapper`)

## Contexto

Cada cambio clínico (registro, consulta, escalamiento, alta) debe quedar en la base de datos, en la auditoría y publicarse en Kafka. Escribir en PostgreSQL y en Kafka como dos operaciones separadas puede dejar el sistema inconsistente: un paciente registrado sin evento, o un evento publicado de un registro que hizo rollback. El asistente y las alertas dependen de esos eventos.

## Decisión

### Publicación: Transactional Outbox

- Paciente, auditoría y fila de `outbox_events` se escriben en **una sola transacción** R2DBC.
- Un relay reactivo (`OutboxRelay`) lee los pendientes y los publica en Kafka:
  - Selecciona con `FOR UPDATE SKIP LOCKED` ordenando por `seq`, de modo que varias réplicas no se pisan.
  - Publica el lote y marca `published_at` dentro de la misma transacción; si Kafka falla, hace rollback y reintenta en el siguiente ciclo.
  - Sondeo cada 500 ms y lotes de 100 (configurables en `triage.outbox`).
- Productor Kafka con `acks=all` y `enable.idempotence=true`.
- Garantía: **at-least-once**. Los consumidores deben deduplicar por `eventId`.

### Formato: JSON con envelope versionado

```json
{
  "eventId": "b3f1c2e0-...",
  "type": "paciente_registrado",
  "version": 1,
  "occurredAt": "2026-10-29T14:03:11Z",
  "correlationId": "7d2a9c...",
  "payload": { "pacienteId": "...", "nivel": 1, "estadoAnterior": null, "estadoNuevo": "REGISTRADO" }
}
```

- Topic `triaje.eventos`; clave de partición = `pacienteId`, lo que preserva el orden de los eventos de un mismo paciente.
- Topic `triaje.eventos.dlt` para mensajes que un consumidor no pueda procesar.
- Tipos: `paciente_registrado`, `paciente_en_consulta`, `paciente_escalado`, `paciente_alta`.
- El `payload` incluye siempre `nivel`, para que el asistente actualice su vista (por ejemplo, decrementar el contador correcto en `paciente_alta`) sin consultar a triage.
- El contrato vive en el módulo `events-contract`; un test (`EventEnvelopeMapperTest`) fija la forma del JSON.

## Alternativas consideradas

- **Publicar en Kafka tras el commit (dual write):** simple, pero se pierden eventos si el proceso cae entre ambos pasos.
- **Transacciones de Kafka + base de datos:** no existe transacción distribuida real entre PostgreSQL y Kafka.
- **CDC con Debezium:** robusto y sin sondeo, pero añade infraestructura (Kafka Connect) desproporcionada para este alcance.
- **Avro/Protobuf con schema registry:** contrato más estricto, pero añade un componente más; el enunciado deja libre el formato y JSON es legible y fácil de documentar.

## Consecuencias

- (+) Nunca hay un cambio de estado sin evento, ni un evento sin cambio.
- (+) La entrega a Kafka se reintenta sola si el broker está caído.
- (−) La latencia de publicación incluye hasta un intervalo de sondeo (≈500 ms).
- (−) Entrega at-least-once: los consumidores deben ser idempotentes (el asistente deduplica por `eventId`).
- (−) La tabla outbox crece; hace falta una política de limpieza de filas publicadas (pendiente).
- (−) Con varias réplicas del relay, el orden estricto entre eventos del mismo paciente depende de `seq` y de que los eventos de un mismo paciente se generen en secuencia; es suficiente para este flujo.
- (−) JSON no valida esquema en el broker; la compatibilidad se gestiona con `version` y cambios aditivos.
