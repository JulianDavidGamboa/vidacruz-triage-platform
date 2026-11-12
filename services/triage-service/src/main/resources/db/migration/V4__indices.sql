-- Índice parcial para el monitor de SLA (RF-03): solo mira pacientes activos sin escalar.
CREATE INDEX idx_pacientes_sla ON pacientes (fecha_registro)
    WHERE estado = 'REGISTRADO' AND escalado = FALSE;

-- Soporte del reporte por hora (RF-05).
CREATE INDEX idx_pacientes_fecha_registro ON pacientes (fecha_registro);
