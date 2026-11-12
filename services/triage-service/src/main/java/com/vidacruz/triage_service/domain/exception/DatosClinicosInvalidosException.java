package com.vidacruz.triage_service.domain.exception;

public class DatosClinicosInvalidosException extends RuntimeException {
  public DatosClinicosInvalidosException(String message) {
    super(message);
  }
}
