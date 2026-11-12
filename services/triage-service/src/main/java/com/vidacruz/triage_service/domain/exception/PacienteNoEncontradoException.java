package com.vidacruz.triage_service.domain.exception;

import java.util.UUID;

/** Se traduce a 404. */
public class PacienteNoEncontradoException extends DomainException {

    public PacienteNoEncontradoException(UUID id) {
        super("No existe un paciente con id " + id);
    }
}