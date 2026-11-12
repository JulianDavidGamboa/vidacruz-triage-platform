package com.vidacruz.assistant_service.domain.model;

/** Tipos de evento de triaje que consume el asistente. Los nombres coinciden con el contrato de eventos. */
public enum TipoEvento {
    PACIENTE_REGISTRADO,
    PACIENTE_EN_CONSULTA,
    PACIENTE_ESCALADO,
    PACIENTE_ALTA
}
