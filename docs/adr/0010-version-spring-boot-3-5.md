# ADR-0010 — Spring Boot 3.5 y Spring AI 1.1.x

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Aplicada en el pom padre (`spring-boot-starter-parent` 3.5.x)

## Contexto

El enunciado exige **Spring Boot 3**. Spring Initializr ofrece hoy por defecto una versión posterior, y Spring Boot 3.5 —la última línea 3.x— salió de soporte de código abierto a finales de junio de 2026. Spring AI 2.0 requiere Boot 4, mientras que la serie 1.x es la compatible con Boot 3.5. Además, versiones recientes de Spring AI 1.x han incluido correcciones de seguridad, por lo que conviene no quedarse en una versión antigua de esa serie.

## Decisión

- Usar **Spring Boot 3.5.x**, el último parche disponible, para cumplir el requisito del enunciado.
- Usar **Spring AI 1.1.x**, la última versión de patch disponible de esa serie (no versiones anteriores a las que incluyen correcciones de seguridad).
- Mantener la dependencia de versiones controlada desde el pom padre (BOM de Spring AI importado allí cuando exista `assistant-service`).
- Dejar **documentado como riesgo conocido** que Boot 3.5 ya no recibe parches de código abierto, y planificar la migración a Boot 4 + Spring AI 2.0 como evolución.
- Reducir el costo de esa migración con la arquitectura hexagonal (ADR-0002): el código específico de Spring queda en la infraestructura y la aplicación, no en el dominio.

## Alternativas consideradas

- **Spring Boot 4 + Spring AI 2.0 desde el inicio:** es la opción con soporte vigente, pero incumple el requisito explícito de Spring Boot 3 y arrastra cambios de migración (por ejemplo, la actualización de Jackson) sin valor para la evaluación.
- **Quedarse en versiones anteriores de Spring AI 1.x:** evita cambios, pero pierde correcciones de seguridad.
- **Soporte comercial para Boot 3.5:** viable en producción, fuera de alcance de esta prueba.

## Consecuencias

- (+) Cumple el requisito del enunciado y usa versiones coherentes entre sí.
- (−) Sin parches de código abierto para Boot 3.5: habrá que vigilar vulnerabilidades en dependencias y planificar la migración.
- (−) Spring AI 1.1.x quedará por detrás de las novedades de la serie 2.x.
- Acción pendiente: revisar el último parche de Boot 3.5.x y de Spring AI 1.1.x antes de fijar versiones definitivas.
