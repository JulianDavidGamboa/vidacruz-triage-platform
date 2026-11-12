# ADR-0007 — Canal de alertas al equipo médico (RF-06)

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Pendiente (RF-06, `alert-service`)

## Contexto

RF-06 exige alertar al equipo médico cuando un paciente es escalado, pero no especifica canal, formato ni tecnología: es una ambigüedad deliberada cuya resolución y justificación forman parte de la evaluación. Hay que decidir el canal, qué información viaja en la alerta y qué pasa cuando el canal falla.

## Decisión

### Canal

- **Webhook de Microsoft Teams** como canal principal: el despliegue objetivo es Azure, el equipo médico y administrativo suele trabajar con herramientas de Microsoft, y un webhook entrante evita integrar un proveedor de SMS o correo para el alcance de la prueba.
- **Canal de respaldo: log estructurado** a nivel `WARN` con marcador de alerta, usado cuando no hay webhook configurado (por ejemplo, en local) o cuando los reintentos se agotan.
- Las URL del webhook y su formato se configuran por variable de entorno (`TEAMS_WEBHOOK_URL`); si está vacía se usa solo el canal de log. Nunca se versiona.

### Arquitectura

- El **`alert-service`** (ADR-0001) consume `paciente_escalado` de `triaje.eventos` con su propio consumer group.
- Un puerto `AlertChannelPort` abstrae el canal; agregar correo, SMS o un panel en tiempo real implica un adaptador nuevo.
- Resiliencia ante fallos del canal: reintentos con backoff (3 intentos) y circuit breaker; si se agotan, la alerta se registra en el canal de respaldo y el mensaje se envía al DLT (`triaje.eventos.dlt`).
- Entrega at-least-once: puede haber alertas duplicadas ante una redelivery; se mitiga con una caché acotada en memoria de `eventId` recientes (los duplicados son tolerables en una alerta, perder una no).

### Contenido de la alerta

Solo identificadores y datos operativos, **sin datos personales ni clínicos**: id del paciente, nivel de triaje, SLA configurado, minutos excedidos y marca de tiempo. El personal consulta el detalle mediante la API autenticada de triaje.

## Supuestos

- Teams es el canal de comunicación del equipo médico.
- Un único canal para las 12 salas, porque el modelo de datos no incluye sala o sede; si se agrega, el `alert-service` puede enrutar por ese dato.
- La alerta es una notificación, no un sistema de acuse ni de escalamiento de guardia.

## Alternativas consideradas

- **Correo electrónico (SMTP):** universal, pero lento de notar para una urgencia y con mala entregabilidad.
- **SMS o llamada (Twilio, Azure Communication Services):** más intrusivo y adecuado para guardias, pero exige proveedor, costos y números del personal.
- **Herramientas de guardia (PagerDuty, Opsgenie):** apropiadas en producción, con rotaciones y acuses, pero fuera de alcance.
- **Panel en tiempo real (SSE/WebSocket):** requiere una interfaz abierta; el enunciado indica que no hay frontend.
- **Enviar los datos clínicos del paciente en la alerta:** más cómodo, pero expone información sensible en un canal de mensajería general.

## Consecuencias

- (+) El triaje no se ve afectado si Teams falla: el canal está aislado en su propio servicio.
- (+) La alerta funciona en local sin credenciales gracias al canal de log.
- (+) Cambiar o sumar canales no toca el consumidor ni el dominio.
- (−) Sin acuse de recibo: no hay garantía de que alguien vea la alerta.
- (−) Puede haber duplicados tras reinicios, ya que la caché de deduplicación es en memoria.
- (−) El formato del mensaje depende del tipo de webhook configurado en Teams; la implementación inicial envía un mensaje de texto simple.
