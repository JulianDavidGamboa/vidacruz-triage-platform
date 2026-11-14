# ADR-0001 — Descomposición en tres servicios

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Implementada: `triage-service`, `assistant-service` y `alert-service` desplegados en AKS

## Contexto

El enunciado exige como mínimo dos microservicios reactivos (Triage y Asistente Clínico) y establece que **el triaje nunca debe depender de la disponibilidad del asistente**. Además, RF-06 pide alertar al equipo médico cuando un paciente es escalado, sin fijar canal ni tecnología. Permite una descomposición adicional siempre que esté justificada.

Los tres comportamientos tienen perfiles de disponibilidad, dependencias externas y ritmo de cambio distintos:

- Triaje es el núcleo clínico: debe ser el más estable y no puede depender de un proveedor de IA ni de un canal de mensajería.
- El asistente depende de un LLM externo, que puede fallar o degradarse.
- Las alertas dependen de un canal externo (webhook) y necesitan reintentos propios.

## Decisión

Tres servicios, comunicados **solo por eventos** en el topic `triaje.eventos`:

| Servicio | Responsabilidad | Dependencias |
|----------|-----------------|--------------|
| `triage-service` | Registro, clasificación, estados, SLA/escalamiento, auditoría, reporte | PostgreSQL, Kafka |
| `assistant-service` | RAG sobre protocolos y vista de urgencias en tiempo real | Kafka, Redis, proveedor de LLM |
| `alert-service` | Notificar al equipo médico cuando hay `paciente_escalado` | Kafka, canal de alertas |

Reglas de dependencia:

- `triage-service` no conoce a los otros dos: ni los llama ni los espera. Solo publica eventos.
- `assistant-service` reconstruye su vista de urgencias consumiendo eventos; no llama a la API de triaje.
- Un módulo compartido `events-contract` contiene únicamente el contrato de eventos (DTOs y constantes), sin lógica.

## Alternativas consideradas

- **Dos servicios, con alertas dentro de triage:** menos piezas, pero acopla el núcleo clínico a un canal externo que falla por razones ajenas al triaje.
- **Dos servicios, con alertas dentro del asistente:** mezcla dos responsabilidades sin relación (IA y notificaciones) y hace que un fallo del LLM arrastre las alertas.
- **Monolito modular:** más simple de operar, pero incumple el requisito de microservicios y de independencia de despliegue.
- **Más descomposición (clasificación, auditoría, reportes por separado):** divide un modelo de datos cohesivo y obligaría a transacciones distribuidas sin beneficio.

## Consecuencias

- (+) Si cae el LLM, el webhook o el asistente completo, el triaje sigue operando.
- (+) Cada servicio escala y se despliega de forma independiente.
- (−) Más piezas que operar: tres imágenes, tres Deployments, más configuración.
- (−) El módulo `events-contract` acopla versiones de los servicios; se mitiga con eventos versionados (`version` en el envelope) y cambios compatibles hacia atrás.
- Si el tiempo apremia, `alert-service` puede reducirse a un segundo consumer group dentro de otro servicio; sería una concesión consciente, no el diseño objetivo.
