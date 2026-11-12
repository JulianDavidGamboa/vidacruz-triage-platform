package com.vidacruz.triage_service.infrastructure.adapter.in.web;

import com.vidacruz.triage_service.application.port.in.ReporteOperacionUseCase;
import com.vidacruz.triage_service.application.port.in.ReporteOperacionVista;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/triage/reportes")
public class ReporteController {

    private final ReporteOperacionUseCase reportes;

    public ReporteController(ReporteOperacionUseCase reportes) {
        this.reportes = reportes;
    }

    /** RF-05: {@code GET /triage/reportes/operacion?desde=...&hasta=...} con instantes ISO 8601. */
    @GetMapping("/operacion")
    public Mono<ReporteOperacionVista> operacion(@RequestParam Instant desde, @RequestParam Instant hasta) {
        return reportes.generar(desde, hasta);
    }
}
