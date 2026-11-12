package com.vidacruz.triage_service.infrastructure.adapter.in.web;

import com.vidacruz.triage_service.domain.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Errores uniformes en formato RFC 7807 (application/problem+json). */
@RestControllerAdvice
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    /** 400: algún campo fuera de rango o faltante; indica cuál. */
    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidation(WebExchangeBindException ex) {
        List<Map<String, String>> errores = ex.getFieldErrors().stream()
                .map(e -> Map.of(
                        "campo", e.getField(),
                        "mensaje", Objects.requireNonNullElse(e.getDefaultMessage(), "valor inválido")))
                .toList();

        String detalle = errores.isEmpty() ? "Solicitud inválida" : errores.get(0).get("mensaje");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problem.setTitle("Datos de entrada inválidos");
        problem.setProperty("errores", errores);
        return problem;
    }

    /** 400: un invariante del dominio fue violado (defensa en profundidad por si el DTO se omite). */
    @ExceptionHandler(DatosClinicosInvalidosException.class)
    public ProblemDetail handleDomain(DatosClinicosInvalidosException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Datos clínicos inválidos");
        problem.setProperty("errores", List.of(Map.of("campo", ex.campo(), "mensaje", ex.getMessage())));
        return problem;
    }

    /** 404: el paciente no existe. */
    @ExceptionHandler(PacienteNoEncontradoException.class)
    public ProblemDetail handleNoEncontrado(PacienteNoEncontradoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Paciente no encontrado");
        return problem;
    }

    /** 409: transición no permitida (RF-02) o cambio concurrente de estado. */
    @ExceptionHandler({TransicionInvalidaException.class, EstadoConcurrenteException.class})
    public ProblemDetail handleConflicto(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflicto de estado clínico");
        return problem;
    }

    /** Respeta el status de errores de framework: JSON mal formado (400), tipo incorrecto, 404, 415, etc. */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String detalle = status.value() == 400
                ? "El cuerpo de la solicitud no es válido (JSON mal formado o tipo de dato incorrecto)"
                : Objects.requireNonNullElse(ex.getReason(), "Error en la solicitud");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalle);
        problem.setTitle(HttpStatus.valueOf(status.value()).getReasonPhrase());
        return problem;
    }

    /** 500: nunca se filtra el detalle interno al cliente. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
        problem.setTitle("Error interno");
        return problem;
    }
}

