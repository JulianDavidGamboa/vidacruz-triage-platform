package com.vidacruz.assistant_service.infrastructure.adapter.in.web;

import com.vidacruz.assistant_service.domain.exception.AsistenteNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Traduce los errores a RFC 7807. El modo degradado responde 503 con un mensaje claro (ADR-0006). */
@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(AsistenteNoDisponibleException.class)
    public ProblemDetail handleNoDisponible(AsistenteNoDisponibleException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Asistente clínico en modo degradado: el proveedor de IA no está disponible");
        problem.setTitle("Asistente no disponible");
        return problem;
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidacion(WebExchangeBindException ex) {
        String detalle = ex.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problem.setTitle("Datos inválidos");
        return problem;
    }
}
