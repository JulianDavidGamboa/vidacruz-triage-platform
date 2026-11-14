---
description: Implementa un requisito funcional pendiente siguiendo la especificación y los ADR
argument-hint: "<RF-05 | RF-04 | RF-06>"
---

Implementa el requisito **$ARGUMENTS** respetando la arquitectura del proyecto.

1. Lee `.claude/CLAUDE.md`, la sección del requisito en `docs/especificacion.md` y el ADR relacionado:
   - RF-05: la especificación (§4) y `docs/adr/0011` para el estilo SQL.
   - RF-04: ADR-0005 (RAG y anti-alucinación) y ADR-0006 (modo degradado).
   - RF-06: ADR-0007.
2. Antes de escribir código, resume en pocas líneas el plan: clases nuevas por capa (`domain`, `application`, `infrastructure`), migraciones y pruebas. Si hay una ambigüedad que cambie el diseño, pregúntala.
3. Implementa de adentro hacia afuera: dominio, puertos y casos de uso, adaptadores y configuración. Reutiliza `ClockPort`, `AuditPort`, `OutboxPort` y `GlobalErrorHandler`; no dupliques.
4. Escribe las pruebas junto con el código: unitarias del dominio y de aplicación, y un `*IT` que extienda `IntegrationTestBase` si toca base de datos o Kafka.
5. Ejecuta `./mvnw verify`. Si no puedes ejecutarlo, dilo.
6. Actualiza el estado en `README.md`, la tabla de §3 y la trazabilidad (§15) de `docs/especificacion.md`, y el ADR si la implementación cambió algo del diseño.
