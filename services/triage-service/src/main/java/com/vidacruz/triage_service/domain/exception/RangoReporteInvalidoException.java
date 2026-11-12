package com.vidacruz.triage_service.domain.exception;

import java.time.Instant;

/** Se traduce a 400: el rango de un reporte debe tener {@code desde} anterior a {@code hasta}. */
public class RangoReporteInvalidoException extends DomainException {

    public RangoReporteInvalidoException(Instant desde, Instant hasta) {
        super("El rango es inválido: desde (%s) debe ser anterior a hasta (%s)".formatted(desde, hasta));
    }
}
