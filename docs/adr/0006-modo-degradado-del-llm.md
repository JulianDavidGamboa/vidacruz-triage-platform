# ADR-0006 — Modo degradado del LLM con circuit breaker

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Implementada (RF-04, `assistant-service`): circuit breaker y 503 en modo degradado, verificados en IT y en AKS. El tope de tiempo usa `.timeout` de Reactor y no un `TimeLimiter`.

## Contexto

El proveedor de LLM es una dependencia externa que puede fallar, ralentizarse o limitar el tráfico. El enunciado exige un circuit breaker de Resilience4j con fallback explícito (respuesta **503** con mensaje claro) y que el servicio de triaje no dependa nunca del asistente. Además, el asistente no debe quedar colgado esperando a un proveedor caído, porque ocuparía recursos del servicio.

## Decisión

- **Resilience4j** protege las llamadas al proveedor de IA, tanto de chat como de embeddings (instancias `llm` y `embeddings`).
- Configuración inicial (ajustable por propiedades):

  | Parámetro | Valor |
  |-----------|-------|
  | Ventana deslizante | 10 llamadas |
  | Umbral de fallos | 50 % |
  | Tiempo en estado abierto | 30 s |
  | Llamadas de prueba en semiabierto | 3 |
  | Timeout por llamada (`TimeLimiter`) | 10 s |

- **Fallback explícito:** con el circuito abierto, o ante timeout o error del proveedor, la API responde **503** con un cuerpo `application/problem+json` y un mensaje claro ("Asistente clínico en modo degradado: el proveedor de IA no está disponible").
- Los errores de entrada del usuario (4xx) y la respuesta "no existe protocolo" **no** cuentan como fallo del circuito.
- Las llamadas bloqueantes del SDK de Spring AI se aíslan con `subscribeOn(Schedulers.boundedElastic())` para no bloquear el event loop.
- `GET /asistente/estado-urgencias` no depende del LLM (solo de Redis y Kafka), por lo que **sigue operativo** en modo degradado.
- Métricas con Micrometer: latencia del LLM (`assistant.llm.latency`), estado del circuit breaker y conteo de fallbacks.
- No se reintenta automáticamente la consulta clínica: es una pregunta interactiva y el médico puede reintentar; un retry silencioso amplificaría la carga sobre un proveedor degradado.

## Alternativas consideradas

- **Responder desde una caché o con un resumen genérico:** arriesgado en contexto clínico; es preferible un 503 explícito a una respuesta potencialmente desactualizada o sin cita.
- **Solo timeouts, sin circuit breaker:** cada petición esperaría el timeout completo con el proveedor caído.
- **Retry con backoff:** útil para operaciones no interactivas; aquí solo agrega latencia.
- **Cambiar automáticamente a un segundo proveedor:** más complejidad y credenciales; queda como evolución posible detrás del mismo puerto.

## Consecuencias

- (+) Con el proveedor caído, el asistente falla rápido y de forma explícita; el triaje no se entera.
- (+) El estado de urgencias sigue disponible aunque no haya IA.
- (−) Durante la ventana abierta del circuito, consultas válidas reciben 503 aunque el proveedor ya se haya recuperado (hasta 30 s).
- (−) Los umbrales requieren ajuste con tráfico real.
- (−) Con el circuito abierto no se atiende ninguna consulta, ni siquiera las que no requerirían generación; por diseño, porque la recuperación también usa embeddings del mismo proveedor (ver ADR-0005).
