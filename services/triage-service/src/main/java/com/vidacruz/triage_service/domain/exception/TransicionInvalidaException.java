package com.vidacruz.triage_service.domain.exception;

import com.vidacruz.triage_service.domain.model.EstadoPaciente;

/** RF-02: transición no permitida por el flujo clínico (ej. ALTA → EN_CONSULTA). Se traduce a 409. */
public class TransicionInvalidaException extends DomainException {

    public TransicionInvalidaException(EstadoPaciente actual, EstadoPaciente destino) {
        super("Transición inválida de %s a %s".formatted(actual, destino));
    }
}