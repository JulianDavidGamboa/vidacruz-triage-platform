package com.vidacruz.events_contract;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EventType {
    PACIENTE_REGISTRADO("paciente_registrado"),
    PACIENTE_EN_CONSULTA("paciente_en_consulta"),
    PACIENTE_ESCALADO("paciente_escalado"),
    PACIENTE_ALTA("paciente_alta");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }
}

