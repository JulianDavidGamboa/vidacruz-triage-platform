package com.vidacruz.triage_service.domain.exception;

/** Raíz de las excepciones de negocio. El dominio no conoce HTTP: el adaptador web las traduce a status. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
