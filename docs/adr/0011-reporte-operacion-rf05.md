# ADR-0011 — Reporte de operación por hora con SQL agregado en PostgreSQL

- **Estado:** Aceptada
- **Fecha:** 2026-11-11
- **Implementación:** Implementada (RF-05): `ReporteOperacionR2dbcAdapter`, `ReporteOperacionService` y `ReporteController`. El `ReporteOperacionIT` está escrito pero no se ha ejecutado, porque requiere Docker.

## Contexto

RF-05 pide un reporte `GET /triage/reportes/operacion?desde=...&hasta=...` agrupado por hora, con total de registros, conteo por nivel, tiempo promedio de espera (registro → primera entrada a consulta, en minutos) y p95 de esa espera. El enunciado exige SQL avanzado y prohíbe iterar en memoria sobre los registros.

## Decisión

- **Agrupación por hora de registro, en UTC.** Una franja cuenta los pacientes registrados en esa hora, y sus tiempos de espera se calculan para esos mismos pacientes.
- **Todo el cálculo ocurre en PostgreSQL.** Una CTE `base` calcula la espera por paciente. Una CTE `por_hora` agrega con `COUNT(*) FILTER (WHERE nivel = n)` para el conteo por nivel, `AVG` para el promedio y `percentile_cont(0.95) WITHIN GROUP` para el p95. Un `RANK() OVER (ORDER BY total DESC)` da el ranking de volumen por franja.
- **Pacientes sin consulta:** cuentan en el total y en el conteo por nivel, pero no en los tiempos de espera. `AVG` y `percentile_cont` ignoran los `NULL`.
- **Rango semiabierto `[desde, hasta)`.** Un `desde` igual o posterior a `hasta` responde 400 (`RangoReporteInvalidoException`) sin consultar la base.
- **Sin migración nueva.** El índice `idx_pacientes_fecha_registro` (V4) ya soporta el filtro por rango.

## Alternativas consideradas

- **Agrupar por hora de primera consulta:** descartada. El reporte describe la demanda que llega, y esa demanda se mide por registro. Agrupar por consulta contaría a los pacientes en otra franja que la de su llegada.
- **Traer las filas y agregar en Java:** prohibido por el enunciado (RF-05) y costoso con volumen real.
- **Vista materializada refrescada por cron:** añade un ciclo de refresco y datos desfasados. Para el volumen de 12 salas, la consulta directa sobre el índice basta.

## Consecuencias

- (+) Un solo viaje a la base por reporte, con SQL verificable en el propio repositorio.
- (+) La agregación no depende del número de registros en memoria: el costo crece con el tamaño del rango, no con el tamaño de la aplicación.
- (−) `percentile_cont` ordena los tiempos de espera del rango; el costo es proporcional a los pacientes de esa ventana.
- (−) La franja es UTC fijo. Un reporte para hora local de Colombia (UTC-5) necesitaría un parámetro de zona horaria; hoy no se pide.
- (−) El SQL solo se ha validado compilando y con pruebas unitarias del servicio. El `ReporteOperacionIT` contra PostgreSQL real debe ejecutarse con Docker antes de dar el requisito por verificado.
