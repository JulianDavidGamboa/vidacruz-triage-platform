package com.vidacruz.triage_service.application.port.out;

import com.vidacruz.triage_service.domain.model.Escalamiento;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface EscalamientoRepositoryPort {

    /**
     * Marca como escalados, en una sola sentencia atómica, a los pacientes en REGISTRADO sin escalar
     * cuyo SLA venció antes de {@code ahora}. Devuelve solo los que esta ejecución escaló: una segunda
     * llamada con la misma fecha devuelve cero (RN-03).
     */
    Flux<Escalamiento> marcarVencidosComoEscalados(Instant ahora);
}
