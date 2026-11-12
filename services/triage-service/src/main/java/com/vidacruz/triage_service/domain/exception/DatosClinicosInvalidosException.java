package com.vidacruz.triage_service.domain.exception;

/** Un dato clínico viola un invariante del dominio (rango, formato o regla cruzada). */
public class DatosClinicosInvalidosException extends DomainException {

    private final String campo;

    public DatosClinicosInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}