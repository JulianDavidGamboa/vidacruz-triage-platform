package com.vidacruz.triage_service.domain.model;

public enum EstadoPaciente {
    REGISTRADO,
    EN_CONSULTA,
    ALTA;

    public boolean puedeTransicionarA(EstadoPaciente destino) {
        return switch (this) {
            case REGISTRADO -> destino == EN_CONSULTA || destino == ALTA;   // incluye alta directa
            case EN_CONSULTA -> destino == ALTA;
            case ALTA -> false;
        };
    }
}
