package com.vidacruz.triage_service.domain.exception;

public class PacienteNoEncontradoException extends RuntimeException {
  public PacienteNoEncontradoException(String message) {
    super(message);
  }
}
