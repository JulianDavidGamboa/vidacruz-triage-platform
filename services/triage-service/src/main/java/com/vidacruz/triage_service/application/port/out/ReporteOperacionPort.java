package com.vidacruz.triage_service.application.port.out;

import com.vidacruz.triage_service.application.port.in.FranjaHorariaVista;
import reactor.core.publisher.Flux;

import java.time.Instant;

/** Lectura agregada por hora de registro en el rango [desde, hasta). Se calcula en la base de datos. */
public interface ReporteOperacionPort {

    Flux<FranjaHorariaVista> resumirPorHora(Instant desde, Instant hasta);
}
