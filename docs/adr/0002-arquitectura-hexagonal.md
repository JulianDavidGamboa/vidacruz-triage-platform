# ADR-0002 — Arquitectura hexagonal por servicio

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Implementada en `triage-service`; reglas verificadas por `HexagonalArchitectureTest` (ArchUnit)

## Contexto

Las reglas de negocio (RN-01 a RN-05) deben poder probarse sin base de datos, Kafka ni LLM, y el enunciado pide que el LLM sea mockeable. Los proveedores externos (LLM, canal de alertas, vector store) tienen más probabilidad de cambiar que las reglas clínicas.

## Decisión

Cada servicio se organiza en tres capas con dependencias que solo apuntan hacia adentro:

```
infrastructure  →  application  →  domain
(adapters, config)  (casos de uso, puertos)  (modelo y reglas puras)
```

- **domain:** modelos, invariantes y reglas (`TriageClassifier`, `SignosVitales`, `Paciente`), sin Spring ni Reactor ni librerías de infraestructura.
- **application:** casos de uso (`port.in`) y puertos de salida (`port.out`). Puede usar Reactor y anotaciones de Spring (`@Service`, `@Transactional`), pero nunca infraestructura.
- **infrastructure:** adaptadores de entrada (REST, scheduler, consumidores Kafka) y de salida (R2DBC, Kafka, Redis, IA), más configuración.

Reglas adicionales:

- Los adaptadores de entrada solo conocen los puertos `in`, no las implementaciones de los servicios.
- Los adaptadores de entrada y de salida no se conocen entre sí.
- Los mappers entre dominio y entidades/mensajes viven en la infraestructura; el dominio no conoce ninguna entidad de base de datos.
- El dominio protege sus invariantes (rangos de signos vitales, nombre, edad); el DTO web valida lo mismo antes, solo para responder con mensajes por campo.
- Estas reglas se hacen cumplir con un test ArchUnit en cada build.

## Alternativas consideradas

- **Capas clásicas (controller → service → repository):** más simple, pero las reglas terminan acopladas a Spring Data y a los DTO, y es más difícil mockear el LLM.
- **Clean Architecture con casos de uso más granulares y entidades separadas por capa:** más ceremonia de la necesaria para este alcance.

## Consecuencias

- (+) El clasificador y las transiciones de estado se prueban con tests unitarios puros (E1–E6 y valores límite).
- (+) Cambiar de proveedor de LLM o de vector store implica escribir un adaptador nuevo, no tocar el dominio.
- (+) La arquitectura queda protegida por tests, no solo por convención.
- (−) Más clases y mappers (dominio ↔ entidad ↔ DTO) que en un diseño por capas.
- (−) Los invariantes validados en dos sitios (DTO y dominio) pueden divergir; los tests de ambos usan los mismos rangos y mensajes.
