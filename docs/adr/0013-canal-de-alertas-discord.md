# ADR-0013 — Canal de alertas adicional: Discord

- **Estado:** Aceptada
- **Fecha:** 2026-11-13
- **Implementación:** Implementada en `alert-service` (`CanalDiscord`, `CanalTeams`, `CanalAlertasCompuesto`). Pruebas unitarias y ArchUnit en verde, y envío real a un canal de Discord verificado en AKS el 2026-11-13. Complementa el ADR-0007 (no lo reemplaza).

## Contexto

El ADR-0007 fijó el webhook de Microsoft Teams como canal principal de las alertas de escalamiento (RF-06), con el log estructurado como respaldo. Al intentar verificar el envío real en el despliegue de demostración no fue posible: el autor solo dispone de una cuenta personal de Teams, y la creación de canales y de flujos de trabajo (Workflows, que reemplazan a los conectores clásicos de webhook) no está disponible ahí. Hoy el canal de Teams solo está probado con un servidor simulado.

Se necesita un canal con el que se pueda demostrar un envío real de extremo a extremo, sin cuentas de organización ni de pago, y sin cambiar el comportamiento ya aceptado en el ADR-0007.

## Decisión

Se añade **Discord** como segundo canal de alertas, mediante un webhook entrante de canal. Convive con Teams: no lo sustituye.

- Un webhook de Discord es una URL a la que se envía un `POST` con un cuerpo JSON de texto. No requiere cuenta de pago ni de organización, y se crea en un servidor propio.
- La URL se configura por variable de entorno, como la de Teams, y nunca se versiona. En Kubernetes va en el `Secret` `vidacruz-secrets`, como clave opcional.
- Cada canal se usa solo si tiene URL configurada. Si no hay ninguno, o si el envío falla, la alerta queda en el log de respaldo, igual que en el ADR-0007.
- El puerto `AlertChannelPort` ya abstrae el canal: Discord se implementa como un adaptador nuevo, sin tocar el dominio ni el consumidor de Kafka. Para repartir la alerta entre los canales configurados se añade un componente de composición en `infrastructure`.
- El contenido de la alerta no cambia: solo identificadores y datos operativos, sin datos personales ni clínicos. La resiliencia (timeout, reintentos con espera creciente y circuit breaker) se aplica por canal, de modo que un canal caído no bloquea al otro.
- El motivo de la elección es práctico: el autor ya tenía experiencia con Discord y la integración es más simple y sin las complicaciones de cuentas de pago u organizativas que exige Teams.

## Alternativas consideradas

- **Reemplazar Teams por Discord:** más simple, pero deja de cumplir el canal definido en el ADR-0007 y obliga a justificarlo como cambio de requisito. Se descartó; Teams se mantiene para cuando haya acceso a una cuenta de organización.
- **Mantener solo Teams:** coherente con el ADR-0007, pero deja sin demostrar el envío real y el formato del mensaje.
- **WhatsApp (Twilio o API oficial de Meta):** exige proveedor, credenciales, un número destino y, en la vía oficial, plantillas de mensaje aprobadas para escribir primero. Mayor esfuerzo y costo para una demostración.
- **Correo, SMS o herramientas de guardia:** ya descartados en el ADR-0007 por las mismas razones.

## Consecuencias

- (+) Permite demostrar un envío real sin cuentas de pago ni de organización.
- (+) Sin cambios en el dominio ni en el consumidor; el cambio queda en un adaptador, un compositor y la configuración.
- (+) El requisito del ADR-0007 sigue cumplido: Teams se mantiene como canal soportado.
- (−) Discord es una plataforma de mensajería de consumo, no un canal corporativo. Aunque la alerta no lleva datos personales ni clínicos, cualquiera con la URL del webhook puede publicar en el canal, por lo que la URL se trata como un secreto.
- (−) Con dos canales hay fallos parciales (uno entrega y el otro no). Se deja registrado en el log; sigue sin haber acuse de recibo.
- (−) Más configuración que mantener y una prueba más por cada canal.
