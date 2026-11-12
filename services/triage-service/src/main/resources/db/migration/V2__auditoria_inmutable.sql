-- RN-04: auditoría clínica append-only.
CREATE TABLE auditoria_clinica (
    id               BIGSERIAL    PRIMARY KEY,
    paciente_id      UUID         NOT NULL REFERENCES pacientes (id),
    tipo_evento      VARCHAR(30)  NOT NULL,
    estado_anterior  VARCHAR(20),
    estado_nuevo     VARCHAR(20)  NOT NULL,
    ts               TIMESTAMPTZ  NOT NULL,
    correlation_id   VARCHAR(64)
);

CREATE INDEX idx_auditoria_paciente ON auditoria_clinica (paciente_id, ts);

-- Ni siquiera con acceso a la BD se puede modificar o borrar una fila de auditoría.
CREATE FUNCTION auditoria_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La auditoría clínica es inmutable (operación % no permitida)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_inmutable
    BEFORE UPDATE OR DELETE ON auditoria_clinica
    FOR EACH ROW EXECUTE FUNCTION auditoria_inmutable();
