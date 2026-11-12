package com.vidacruz.triage_service.application.service;

import com.vidacruz.triage_service.application.CorrelationContext;
import com.vidacruz.triage_service.application.port.in.EscalarPacientesVencidosUseCase;
import com.vidacruz.triage_service.application.port.out.*;
import com.vidacruz.triage_service.domain.model.EventoClinico;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class EscalarPacientesVencidosService implements EscalarPacientesVencidosUseCase {

    private final EscalamientoRepositoryPort escalamientos;
    private final AuditPort auditoria;
    private final OutboxPort outbox;
    private final MetricsPort metrics;
    private final ClockPort clock;

    public EscalarPacientesVencidosService(EscalamientoRepositoryPort escalamientos, AuditPort auditoria,
                                           OutboxPort outbox, MetricsPort metrics, ClockPort clock) {
        this.escalamientos = escalamientos;
        this.auditoria = auditoria;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Marcado, auditoría y outbox en UNA transacción: si algo falla, no queda ningún paciente escalado
     * sin su evento, y la siguiente ejecución lo reintenta. Los escalamientos se materializan antes de
     * escribir porque una conexión R2DBC no admite otra consulta mientras el resultado sigue abierto.
     */
    @Override
    @Transactional
    public Mono<Long> ejecutar() {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationContext.KEY, "sla-sin-correlacion");
            Instant ahora = clock.ahora();

            return escalamientos.marcarVencidosComoEscalados(ahora)
                    .collectList()
                    .flatMapMany(Flux::fromIterable)
                    .concatMap(escalado -> {
                        EventoClinico evento = EventoClinico.escalamiento(escalado, correlationId);
                        return auditoria.registrar(evento)
                                .then(outbox.guardar(evento))
                                .doOnSuccess(ignored -> metrics.escalamientoRegistrado())
                                .thenReturn(1L);
                    })
                    .count();
        });
    }
}
