# ADR-0005 — Vector store en memoria y política anti-alucinación

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Implementada (RF-04, `assistant-service`): compuerta por similitud y validación de citas con pruebas unitarias. El umbral final en AKS es 0,40 con otro modelo de embeddings (ver `docs/especificacion.md`, §14); el indicador de salud descrito aquí no existe y el servicio responde 503 si el corpus no cargó.

## Contexto

El asistente clínico responde con IA generativa, pero "nunca puede inventar contenido": toda respuesta debe estar sustentada en el corpus oficial con cita de la fuente (RN-05). Si no hay protocolo relevante, la respuesta debe ser **exclusivamente** el aviso de que no existe protocolo disponible. El corpus mínimo son tres protocolos breves; el enunciado deja libre el vector store y pide documentarlo en un ADR, además de elegir entre Spring AI y LangChain4j.

## Decisión

### Framework y vector store

- **Spring AI** (BOM de la serie 1.1.x, ver ADR-0010): integra con Spring Boot, expone abstracciones `ChatClient`/`VectorStore`/`EmbeddingModel` y permite mockear el modelo en tests.
- **`SimpleVectorStore` en memoria**, cargado al arrancar desde `resources/protocolos/*.md`, dividido por secciones (por ejemplo, "Medidas iniciales", "Contraindicaciones"). Cada fragmento lleva como metadata el `protocolo` (PROTO-001…) y la `seccion`.
- Un puerto `ProtocolRetrieverPort` aísla la recuperación: cambiar a pgvector o a otro store implica escribir un adaptador nuevo.
- Se usa un único proveedor para chat y embeddings, para simplificar credenciales (la API key va por variable de entorno).

### Política anti-alucinación (defensa en dos puntos)

1. **Antes del LLM — compuerta por similitud.** Se recuperan los `top-k` fragmentos (inicialmente 3). Si ninguno supera el **umbral de similitud** configurable (`assistant.rag.similarity-threshold`, valor inicial 0,75, a calibrar con las tres consultas de ejemplo), la respuesta es el aviso fijo "No existe un protocolo disponible para la pregunta", con `fuentes` vacío, **sin llamar al LLM**. Esto hace que el caso de la diabetes tipo 2 sea determinista y no dependa del comportamiento del modelo.
2. **Después del LLM — validación de citas.** El prompt restringe al modelo al contexto recuperado (temperatura 0). Las `fuentes` de la respuesta se **construyen a partir de la metadata de los fragmentos recuperados**, nunca del texto que genera el modelo. Si la respuesta del modelo está vacía o referencia protocolos fuera del conjunto recuperado, se considera inválida y se devuelve el aviso fijo de "no existe protocolo", sin contenido clínico (RN-05).

## Alternativas consideradas

- **pgvector:** persistente y escalable, pero añade una base de datos al asistente para un corpus de tres documentos.
- **Redis con búsqueda vectorial:** Redis ya está en el despliegue, pero requiere una imagen con módulo de búsqueda y complica el resto del uso (contadores simples).
- **LangChain4j:** válido, pero con Spring AI se reutilizan el BOM, la configuración y la observabilidad de Spring Boot.
- **Confiar en la instrucción del prompt ("no inventes"):** insuficiente para un contexto clínico; la compuerta y la validación posterior no dependen del modelo.

## Consecuencias

- (+) Cero infraestructura adicional y arranque simple; el caso "sin coincidencia" no consume LLM ni puede alucinar.
- (+) Las citas son verificables: salen de los metadatos del corpus.
- (−) Los embeddings se recalculan en cada arranque; es irrelevante con tres protocolos, pero para un corpus grande habrá que persistir el índice o migrar a pgvector (ya aislado tras el puerto).
- (−) Cada réplica mantiene su propio índice en memoria; es consistente porque parten del mismo corpus versionado en el repositorio.
- (−) El umbral exige calibración: si es muy alto rechaza preguntas válidas; si es muy bajo deja pasar fragmentos poco relevantes. Se calibra con las consultas de ejemplo y se cubre con tests.
- (−) La recuperación requiere el servicio de embeddings: si no está disponible al arrancar, el asistente no queda listo (readiness DOWN), sin afectar al triaje.
