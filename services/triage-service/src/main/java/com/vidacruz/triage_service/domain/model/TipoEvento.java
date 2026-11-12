package com.vidacruz.triage_service.domain.model;

/** Los nombres coinciden 1:1 con com.vidacruz.contracts.EventType. */
public enum TipoEvento {
    PACIENTE_REGISTRADO,
    PACIENTE_EN_CONSULTA,
    PACIENTE_ESCALADO,
    PACIENTE_ALTA
}
