package com.vidacruz.triage_service.domain.exception;

import java.util.UUID;

/**
 * El estado cambió entre la lectura y la escritura (otra petición o el escalamiento). Se traduce a 409:
 * el cliente debe volver a consultar el paciente antes de reintentar.
 */
public class EstadoConcurrenteException extends DomainException {

    public EstadoConcurrenteException(UUID id) {
        super("El paciente " + id + " cambió de estado mientras se procesaba la solicitud; vuelva a consultarlo");
    }
}

