# ADR-0004 — Escalamiento de SLA con SQL atómico

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Implementada (RF-03): `EscalamientoR2dbcAdapter`, `EscalarPacientesVencidosService` y `SlaMonitorScheduler`

## Contexto

RF-03 exige evaluar periódicamente a los pacientes en `REGISTRADO` y marcar como escalados a los que superaron su SLA, con tres condiciones de negocio (RN-03): el escalamiento ocurre **una única vez**, un paciente en `EN_CONSULTA` o `ALTA` **nunca** se escala, y el segundo intento no publica otro evento ni duplica auditoría. El intervalo y los SLA deben ser configurables para poder probar sin esperar decenas de minutos.

## Decisión

Un scheduler reactivo (`Flux.interval`, intervalo configurable) ejecuta una única sentencia que selecciona y marca en un solo paso:

```sql
WITH vencidos AS (
    SELECT id FROM pacientes
    WHERE estado = 'REGISTRADO'
      AND escalado = FALSE
      AND fecha_registro + make_interval(mins => sla_minutos) < :ahora
    FOR UPDATE SKIP LOCKED
)
UPDATE pacientes p
SET escalado = TRUE, fecha_escalado = :ahora
FROM vencidos v WHERE p.id = v.id
RETURNING p.id, p.nivel;
```

Para cada fila devuelta, y en la **misma transacción**, se registra la auditoría y se escribe el evento `paciente_escalado` en el outbox.

- **Idempotencia por construcción:** la condición `estado = 'REGISTRADO' AND escalado = FALSE` hace que un paciente ya escalado, en consulta o dado de alta no vuelva a seleccionarse; la segunda ejecución devuelve cero filas y no genera evento ni auditoría.
- **El reloj es inyectable:** el instante llega como parámetro (`:ahora`, tomado de `ClockPort`) en lugar de `now()`. Así los tests fijan el tiempo y comprueban el límite exacto (en `T0 + SLA` no escala; un segundo después sí) sin esperar minutos. La regla equivalente en el dominio es `Paciente.superaSla(ahora)`.
- **Seguro con varias réplicas:** `FOR UPDATE SKIP LOCKED` evita que dos instancias escalen al mismo paciente.
- "Escalado" es un **flag persistente**, no un estado: el paciente sigue en `REGISTRADO`, así que las transiciones válidas de RF-02 no cambian.
- El SLA se **congela al registrar** (`sla_minutos` en la fila): cambiar la configuración no altera retroactivamente a los pacientes ya registrados.
- Configuración: `triage.sla.minutos.*`, `triage.sla.evaluation-interval` (30 s por defecto, variable `TRIAGE_SLA_EVALUATION_INTERVAL`) y `triage.sla.monitor-enabled` (`TRIAGE_SLA_MONITOR_ENABLED`; permite apagar el scheduler en las pruebas). Los SLA se validan al arrancar: faltar un nivel o no ser positivo impide iniciar el servicio.
- El scheduler (`Flux.interval` + `concatMap`) evita que dos ciclos se solapen dentro de una misma instancia, y un error en un ciclo no detiene los siguientes.
- El caso de uso materializa los escalados (`collectList`) antes de auditar y publicar, porque una conexión R2DBC no admite otra consulta mientras el resultado del `RETURNING` sigue abierto.
- Un índice parcial (`idx_pacientes_sla`, ya creado) limita la consulta a pacientes activos sin escalar.

## Alternativas consideradas

- **Leer y filtrar en memoria, luego actualizar:** carrera entre instancias y entre "leer" y "escribir"; la idempotencia dependería de código, no de la base de datos.
- **Temporizador por paciente (en memoria o mensajes diferidos en Kafka):** se pierde el estado al reiniciar y es difícil de configurar para pruebas.
- **Quartz o ShedLock (una sola instancia activa):** añade dependencias y un punto único de ejecución; `SKIP LOCKED` ya resuelve la concurrencia.
- **Consumidor que reacciona a eventos de "vencimiento":** requiere otra pieza que genere esos eventos en el tiempo.

## Consecuencias

- (+) RN-03 la garantiza la base de datos, no la disciplina del código.
- (+) Para probar, basta bajar el intervalo y los SLA en la configuración.
- (−) El escalamiento tiene una latencia de hasta un intervalo de evaluación; un SLA de 5 minutos con un intervalo de 30 s tolera un retraso máximo de 30 s.
- (+) La idempotencia y la ausencia de dobles escalamientos concurrentes se prueban con una base de datos real (`EscalamientoIT`: ejecución repetida y dos ejecuciones simultáneas).
- (−) Un paciente que pasa a consulta justo antes de que corra el scheduler puede competir con el escalamiento; la transición de estado usa `UPDATE ... WHERE estado = 'REGISTRADO'`, de modo que gana quien confirme primero y el otro no produce efecto.
