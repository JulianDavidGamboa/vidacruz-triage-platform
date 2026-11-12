package com.vidacruz.assistant_service.domain.exception;

/** El asistente no puede responder porque el proveedor de IA o el corpus no están disponibles (ADR-0006). */
public class AsistenteNoDisponibleException extends RuntimeException {

    public AsistenteNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public AsistenteNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
