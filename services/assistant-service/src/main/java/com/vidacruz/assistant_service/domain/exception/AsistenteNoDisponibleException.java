package com.vidacruz.assistant_service.domain.exception;

public class AsistenteNoDisponibleException extends RuntimeException {

    public AsistenteNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public AsistenteNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
