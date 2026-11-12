# ADR-0008 — Autenticación por API key

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Implementada en `triage-service` (`ApiKeyAuthFilter`, `SecurityConfig`); replicable en el asistente

## Contexto

El enunciado exige autenticación básica en los endpoints (JWT o API key, a elección, documentada), sin credenciales hardcodeadas y con validación de entradas. Es un backend de APIs y eventos, sin frontend ni gestión de usuarios.

## Decisión

- **API key en el header `X-API-Key`**, cuyo valor llega por variable de entorno (`API_KEY`). El servicio no arranca si falta.
- La comparación se hace en **tiempo constante** (`MessageDigest.isEqual`) para evitar ataques de temporización.
- Sin sesiones ni cookies: API sin estado, CSRF y login de formulario deshabilitados.
- Una petición sin clave válida recibe **401** con cuerpo `application/problem+json`.
- Los probes de salud (`/actuator/health/**`) son públicos para que Kubernetes pueda consultarlos; el resto de endpoints, incluido `/actuator/prometheus`, exige la clave.
- El `correlationId` se acepta por `X-Correlation-Id` solo si cumple un formato seguro (`[A-Za-z0-9._-]{1,64}`), para evitar inyección en logs.
- En despliegue, la clave vive en un secreto (Azure Key Vault montado en el pod), no en el repositorio ni en la imagen.

## Alternativas consideradas

- **JWT con Microsoft Entra ID:** es la opción natural en Azure y aporta identidad por usuario, pero exige un proveedor de identidad, emisión de tokens y gestión de roles: desproporcionado para el alcance.
- **HTTP Basic:** requiere gestionar usuarios y contraseñas sin aportar más que una API key.
- **mTLS entre servicios:** adecuado en producción, pero complica el entorno local y la prueba.

## Consecuencias

- (+) Implementación mínima, sin dependencias externas y fácil de probar.
- (+) Sin credenciales en el código; falla rápido si falta configuración.
- (−) **No hay identidad por usuario:** todas las llamadas comparten la clave; por eso la auditoría clínica (RN-04) registra eventos, no quién los originó.
- (−) Rotar la clave implica actualizar el secreto y reiniciar los servicios.
- (−) Prometheus debe enviar la clave o scrapear por un puerto de gestión separado; queda pendiente para el despliegue.
- Evolución prevista: pasar a JWT con Entra ID cuando se necesite identidad por usuario y roles, sustituyendo solo el filtro de autenticación.
