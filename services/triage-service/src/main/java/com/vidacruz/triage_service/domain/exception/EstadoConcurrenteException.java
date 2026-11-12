package com.vidacruz.triage_service.domain.exception;

public class EstadoConcurrenteException extends RuntimeException {
  public EstadoConcurrenteException(String message) {
    super(message);
  }
}
