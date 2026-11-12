package com.vidacruz.triage_service.application.service;

import com.vidacruz.triage_service.application.port.in.ReporteOperacionUseCase;
import com.vidacruz.triage_service.application.port.in.ReporteOperacionVista;
import com.vidacruz.triage_service.application.port.out.ReporteOperacionPort;
import com.vidacruz.triage_service.domain.exception.RangoReporteInvalidoException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class ReporteOperacionService implements ReporteOperacionUseCase {

    private final ReporteOperacionPort reportes;

    public ReporteOperacionService(ReporteOperacionPort reportes) {
        this.reportes = reportes;
    }

    @Override
    public Mono<ReporteOperacionVista> generar(Instant desde, Instant hasta) {
        if (!desde.isBefore(hasta)) {
            return Mono.error(new RangoReporteInvalidoException(desde, hasta));
        }
        return reportes.resumirPorHora(desde, hasta)
                .collectList()
                .map(franjas -> new ReporteOperacionVista(desde, hasta, franjas));
    }
}
