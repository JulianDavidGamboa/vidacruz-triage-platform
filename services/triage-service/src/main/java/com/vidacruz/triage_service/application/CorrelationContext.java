package com.vidacruz.triage_service.application;

/** Clave con la que viaja el correlationId en el Reactor Context. */
public final class CorrelationContext {
    public static final String KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationContext() {
    }
}
