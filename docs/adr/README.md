# Architecture Decision Records (ADR)

Registro de las decisiones de arquitectura de la plataforma de triaje digital VidaCruz.
Formato: contexto → decisión → alternativas → consecuencias. Cada ADR indica su **estado de implementación**.

| # | Decisión | Estado | Implementación |
|---|----------|--------|----------------|
| [0001](0001-descomposicion-de-servicios.md) | Descomposición en tres servicios | Aceptada | Triage implementado (RF-01); asistente y alertas pendientes |
| [0002](0002-arquitectura-hexagonal.md) | Arquitectura hexagonal por servicio | Aceptada | Implementada en triage, con test ArchUnit |
| [0003](0003-outbox-y-formato-de-eventos.md) | Transactional Outbox y formato JSON de eventos | Aceptada | Implementada (RF-01) |
| [0004](0004-estrategia-de-escalamiento-sla.md) | Escalamiento de SLA con SQL atómico | Aceptada | Implementada (RF-03) |
| [0005](0005-vector-store-y-anti-alucinacion.md) | Vector store en memoria y política anti-alucinación | Aceptada | Implementado (RF-04); compuerta por similitud y validación de fuentes con pruebas unitarias |
| [0006](0006-modo-degradado-del-llm.md) | Modo degradado del LLM con circuit breaker | Aceptada | Implementado (RF-04); 503 verificado en IT |
| [0007](0007-canal-de-alertas-rf06.md) | Canal de alertas al equipo médico (RF-06) | Aceptada | Implementado (RF-06); contrato version 2 con SLA y fecha de registro |
| [0008](0008-autenticacion-api-key.md) | Autenticación por API key | Aceptada | Implementada en triage |
| [0009](0009-despliegue-aks-y-event-hubs.md) | Despliegue en AKS con servicios gestionados de Azure | Aceptada | Pendiente |
| [0010](0010-version-spring-boot-3-5.md) | Spring Boot 3.5 y Spring AI 1.1.x | Aceptada | Aplicada en el pom padre |
| [0011](0011-reporte-operacion-rf05.md) | Reporte de operación por hora con SQL agregado | Aceptada | Implementada (RF-05); `ReporteOperacionIT` verificado con PostgreSQL 16 |

## Plantilla

```markdown
# ADR-NNNN — Título
- **Estado:** Propuesta | Aceptada | Reemplazada por ADR-XXXX
- **Fecha:** AAAA-MM-DD
- **Implementación:** ...
## Contexto
## Decisión
## Alternativas consideradas
## Consecuencias
```
